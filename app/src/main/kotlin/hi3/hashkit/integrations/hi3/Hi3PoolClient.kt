package hi3.hashkit.integrations.hi3

import hi3.hashkit.discovery.MinerHostValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only client for the Hi3 Pool API (a public-pool fork), verified against
 * pool.hi3.cc v0.23.x and its compiled backend controllers:
 *
 *  - GET /api/client/{payoutAddress}[?payoutMode=..] -> { workersCount, workers[] }
 *      worker: { sessionId, name, bestDifficulty, hashRate, startTime, lastSeen }
 *  - GET /api/info    -> pool stats (user agents, high scores)
 *  - GET /api/network -> bitcoind-style network info (difficulty, height, ...)
 *
 * OPT-IN ONLY. Nothing here runs unless the user enables Hi3 Pool in Settings.
 * What leaves the device per refresh: the configured payout address inside the
 * request path, over HTTPS, to the configured pool host — no miner telemetry, no
 * local IPs, no worker credentials. Public pool hosts must use HTTPS; plain HTTP is
 * permitted only toward private/Tailscale addresses (e.g. a local stage instance).
 */
@Singleton
class Hi3PoolClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class PoolWorker(
        val sessionId: String?,
        val name: String,
        val bestDifficulty: Double?,
        val hashRateGhs: Double?,
        val startTime: String?,
        val lastSeen: String?,
    )

    data class PoolAccount(
        val workersCount: Int,
        val workers: List<PoolWorker>,
        /** Sum of pool-side worker hashrates, GH/s. */
        val totalHashRateGhs: Double,
    )

    data class PoolNetwork(
        val difficulty: Double?,
        val blockHeight: Long?,
    )

    /** One rig as the stratum proxy sees it (from /sproxy-api/api/v1/sessions). */
    data class PoolSession(
        val worker: String,
        val peerHost: String?,   // the rig's LAN IP, e.g. "10.0.0.48"
        val hashRateGhs: Double?,
        val sharesAccepted: Long?,
        val sharesRejected: Long?,
        val sharesStale: Long?,
    )

    sealed interface PoolResult<out T> {
        data class Ok<T>(val value: T, val rawBody: String) : PoolResult<T>
        data class Error(val message: String) : PoolResult<Nothing>
    }

    /** Validates scheme+host rules for a configured pool base URL. Returns an error string or null. */
    fun validateBaseUrl(baseUrl: String): String? {
        val url = baseUrl.trim().removeSuffix("/").toHttpUrlOrNull()
            ?: return "Not a valid URL."
        if (!url.isHttps && !MinerHostValidator.resolvesToAllowed(url.host)) {
            return "Public pool hosts must use HTTPS (plain HTTP is allowed only for private/Tailscale addresses)."
        }
        return null
    }

    suspend fun fetchAccount(baseUrl: String, payoutAddress: String): PoolResult<PoolAccount> {
        val address = payoutAddress.trim()
        if (address.isEmpty()) return PoolResult.Error("No payout address configured.")
        if (!address.matches(Regex("^[a-zA-Z0-9]{20,90}$"))) {
            return PoolResult.Error("Payout address has an unexpected format.")
        }
        return get(baseUrl, "/api/client/$address") { body ->
            val obj = json.parseToJsonElement(body).jsonObject
            val workers = (obj["workers"] as? JsonArray)?.mapNotNull { el ->
                val w = el as? JsonObject ?: return@mapNotNull null
                PoolWorker(
                    sessionId = w.str("sessionId"),
                    name = w.str("name") ?: return@mapNotNull null,
                    bestDifficulty = w.num("bestDifficulty"),
                    // public-pool reports hashRate in H/s; normalize to GH/s.
                    hashRateGhs = w.num("hashRate")?.div(1e9),
                    startTime = w.str("startTime"),
                    lastSeen = w.str("lastSeen"),
                )
            }.orEmpty()
            PoolAccount(
                workersCount = (obj["workersCount"] as? JsonPrimitive)?.content?.toIntOrNull()
                    ?: workers.size,
                workers = workers,
                totalHashRateGhs = workers.sumOf { it.hashRateGhs ?: 0.0 },
            )
        }
    }

    /**
     * Per-rig sessions from the stratum proxy, filtered to [payoutAddress]. The public
     * pool gates this endpoint (403) because it exposes rig LAN IPs; it succeeds when
     * the app points at an authorized/internal pool URL. Returns an empty list (not an
     * error) when gated, so the caller can fall back to aggregate comparison.
     */
    suspend fun fetchSessions(baseUrl: String, payoutAddress: String): PoolResult<List<PoolSession>> =
        get(baseUrl, "/sproxy-api/api/v1/sessions") { body ->
            val arr = json.parseToJsonElement(body).jsonObject["sessions"] as? JsonArray
            arr.orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val worker = o.str("worker") ?: return@mapNotNull null
                if (payoutAddress.isNotBlank() && !worker.startsWith(payoutAddress)) return@mapNotNull null
                PoolSession(
                    worker = worker,
                    peerHost = o.str("peer")?.substringBeforeLast(':'),
                    hashRateGhs = o.num("hashrate")?.div(1e9),
                    sharesAccepted = o.num("shares_accepted")?.toLong(),
                    sharesRejected = o.num("shares_rejected")?.toLong(),
                    sharesStale = o.num("shares_stale")?.toLong(),
                )
            }
        }

    suspend fun fetchNetwork(baseUrl: String): PoolResult<PoolNetwork> =
        get(baseUrl, "/api/network") { body ->
            val obj = json.parseToJsonElement(body).jsonObject
            PoolNetwork(
                difficulty = obj.num("difficulty"),
                blockHeight = obj.num("blocks")?.toLong(),
            )
        }

    private suspend fun <T> get(
        baseUrl: String,
        path: String,
        parse: (String) -> T,
    ): PoolResult<T> = withContext(Dispatchers.IO) {
        validateBaseUrl(baseUrl)?.let { return@withContext PoolResult.Error(it) }
        val url = (baseUrl.trim().removeSuffix("/") + path).toHttpUrlOrNull()
            ?: return@withContext PoolResult.Error("Invalid request URL.")
        val request = Request.Builder().url(url).get().build()
        try {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext PoolResult.Error("Pool answered HTTP ${resp.code}.")
                }
                val body = resp.body?.string().orEmpty()
                runCatching { PoolResult.Ok(parse(body), body) }
                    .getOrElse { PoolResult.Error("Unexpected pool response format.") }
            }
        } catch (e: IOException) {
            PoolResult.Error(e.message ?: "Network error reaching the pool.")
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.num(key: String): Double? =
        (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
}
