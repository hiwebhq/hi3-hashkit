package hi3.hashkit.data.provision

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.PatternMatcher
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import hi3.hashkit.adapters.espminer.EspMinerFirmware
import hi3.hashkit.adapters.espminer.EspMinerFlavor
import hi3.hashkit.adapters.espminer.EspMinerParser
import hi3.hashkit.adapters.espminer.ParsedSystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * First-boot setup of a Bitaxe straight from the phone, replacing the captive-portal dance.
 *
 * Verified against the ESP-Miner source: an unconfigured Bitaxe runs an OPEN soft-AP named
 * `Bitaxe_XXXX` (last two MAC octets, components/connect/connect.c) at the ESP-IDF default
 * gateway 192.168.4.1, and its normal HTTP API is live on it. Wi-Fi credentials are written
 * with `PATCH /api/system` keys `ssid` (1–32 chars) and `wifiPass` (0–63), `hostname` is
 * normalised by the firmware, and pools use the same flat/array shape as any other pool
 * edit (nvs_config.c / http_server.c). `POST /api/system/restart` then makes it join the
 * home network.
 *
 * The phone joins the AP through [WifiNetworkSpecifier] (Android 10+), which hands back a
 * [Network] that is bound to this process only and carries no internet — every request here
 * is pinned to that network's socket factory, so the home Wi-Fi password travels over the
 * direct link to the miner and nowhere else. Nothing is stored.
 */
@Singleton
class BitaxeProvisioner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val baseClient: OkHttpClient,
) {
    data class PoolSetup(val url: String, val port: Int, val worker: String)

    /** Home network + optional name for the unit. */
    data class WifiSetup(val ssid: String, val password: String, val hostname: String? = null)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var callback: ConnectivityManager.NetworkCallback? = null

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Ask Android to join a `Bitaxe_*` network; the system shows its own picker. Returns
     * the bound [Network], or null when the user cancelled / nothing matched in time.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun connect(): Network? {
        disconnect()
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsidPattern(PatternMatcher(AP_SSID_PREFIX, PatternMatcher.PATTERN_PREFIX))
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (cont.isActive) cont.resume(network)
                    }
                    override fun onUnavailable() {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                callback = cb
                cm.requestNetwork(request, cb)
                cont.invokeOnCancellation { runCatching { cm.unregisterNetworkCallback(cb) } }
            }
        }
    }

    /** Release the AP network so Android returns the phone to its normal Wi-Fi. */
    fun disconnect() {
        val cb = callback ?: return
        callback = null
        runCatching {
            context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb)
        }
    }

    /** Identity/telemetry of the Bitaxe at the AP gateway, or null if it doesn't answer. */
    suspend fun readInfo(network: Network): ParsedSystemInfo? = withContext(Dispatchers.IO) {
        runCatching {
            clientOn(network).newCall(Request.Builder().url("$BASE_URL/api/system/info").get().build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) null else EspMinerParser.parseSystemInfo(resp.body?.string().orEmpty())
                }
        }.getOrNull()
    }

    /**
     * Write Wi-Fi + optional hostname/pool, then restart so the miner joins [homeSsid].
     * Returns null on success, else a message.
     */
    suspend fun provision(
        network: Network,
        wifi: WifiSetup,
        pool: PoolSetup?,
    ): String? = withContext(Dispatchers.IO) {
        if (wifi.ssid.isBlank() || wifi.ssid.length > MAX_SSID) return@withContext "Wi-Fi name must be 1–32 characters."
        if (wifi.password.length > MAX_WIFI_PASS) return@withContext "Wi-Fi password must be at most 63 characters."
        val client = clientOn(network)
        val info = runCatching {
            client.newCall(Request.Builder().url("$BASE_URL/api/system/info").get().build()).execute().use { r ->
                if (r.isSuccessful) json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject else null
            }
        }.getOrNull() ?: return@withContext "The Bitaxe stopped answering; stay near it and try again."
        val flavor = EspMinerFirmware.flavorOf(EspMinerParser.parseSystemInfo(info.toString())?.identity)
        val payload = buildPayload(info, flavor, wifi, pool)
            ?: return@withContext "This firmware isn't one Hashkit knows how to set up; use its web page at $BASE_URL."
        val patched = runCatching {
            val request = Request.Builder().url("$BASE_URL/api/system")
                .patch(payload.toString().toRequestBody(JSON_TYPE)).build()
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
        if (!patched) return@withContext "The Bitaxe rejected the settings."
        // The restart drops the AP; a closed connection here is the expected outcome.
        runCatching {
            val restart = Request.Builder().url("$BASE_URL/api/system/restart")
                .post("".toRequestBody(JSON_TYPE)).build()
            client.newCall(restart).execute().close()
        }
        null
    }

    private fun clientOn(network: Network): OkHttpClient = baseClient.newBuilder()
        .socketFactory(network.socketFactory)
        .connectTimeout(AP_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(AP_TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(AP_TIMEOUT_S * 2, TimeUnit.SECONDS)
        .build()

    companion object {
        const val AP_SSID_PREFIX = "Bitaxe_"
        const val AP_GATEWAY = "192.168.4.1"
        const val BASE_URL = "http://$AP_GATEWAY"
        private const val MAX_SSID = 32
        private const val MAX_WIFI_PASS = 63
        private const val CONNECT_TIMEOUT_MS = 90_000L
        private const val AP_TIMEOUT_S = 6L
        private val JSON_TYPE = "application/json".toMediaType()

        /**
         * The PATCH body: Wi-Fi keys always, hostname when given, and the pool in whichever
         * shape this firmware uses (flat fields ≤ v2.14, `pools` array from v2.15 with the
         * stored-password mask echoed, exactly like EspMinerAdapter.setPrimaryPool). Null
         * for flavors whose settings API isn't verified.
         */
        fun buildPayload(
            info: JsonObject,
            flavor: EspMinerFlavor,
            wifi: WifiSetup,
            pool: PoolSetup?,
        ): JsonObject? {
            if (!EspMinerFirmware.controlsSupported(flavor)) return null
            return buildJsonObject {
                put("ssid", wifi.ssid.trim())
                put("wifiPass", wifi.password)
                wifi.hostname?.trim()?.takeIf { it.isNotEmpty() }?.let { put("hostname", it) }
                if (pool != null) {
                    when (flavor) {
                        EspMinerFlavor.OFFICIAL_V2_FLAT_POOLS -> {
                            put("stratumURL", pool.url)
                            put("stratumPort", pool.port)
                            put("stratumUser", pool.worker)
                        }
                        EspMinerFlavor.OFFICIAL_V2_POOLS_ARRAY -> put("pools", editedPools(info, pool))
                        else -> Unit
                    }
                }
            }
        }

        private fun editedPools(info: JsonObject, pool: PoolSetup): JsonArray {
            val pools = info["pools"] as? JsonArray
            val primaryIndex = (info["primaryPoolIndex"] as? JsonPrimitive)?.intOrNull ?: 0
            if (pools == null || pools.isEmpty()) {
                // A factory-fresh unit may report no pools yet: a single primary entry.
                return buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", 0); put("stratumURL", pool.url); put("stratumPort", pool.port)
                            put("stratumUser", pool.worker)
                        },
                    )
                }
            }
            return buildJsonArray {
                pools.forEachIndexed { i, el ->
                    val obj = el.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.intOrNull
                    if (id == primaryIndex || (id == null && i == primaryIndex)) {
                        add(
                            JsonObject(
                                obj.toMutableMap().apply {
                                    put("stratumURL", JsonPrimitive(pool.url))
                                    put("stratumPort", JsonPrimitive(pool.port))
                                    put("stratumUser", JsonPrimitive(pool.worker))
                                },
                            ),
                        )
                    } else {
                        add(el)
                    }
                }
            }
        }
    }
}
