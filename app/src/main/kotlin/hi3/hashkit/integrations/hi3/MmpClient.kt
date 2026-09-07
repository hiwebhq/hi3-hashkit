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
 * Read-only client for the Hi3 Mining Management Platform API, verified against the
 * deployed OpenAPI contract (Hi3 MMP 0.16.x) and its auth guard:
 *
 *  - Auth: `Authorization: Bearer <mmp_... API key>` (keys are minted in the MMP
 *    admin UI with scoped read permissions; the guard answers 401 for an unknown key
 *    and 403 for a known key lacking the permission — surfaced distinctly here).
 *  - GET /api/v1/fleet/summary  -> FleetSummary (installed/online/healthy/
 *      needs_attention/zero_hash, hashrate_ths, hashrate_nominal_ths,
 *      hashrate_realization_pct, power_kw, efficiency_jth, availability_pct)
 *  - GET /api/v1/fleet/by-site  -> [SiteRollup {site_id, site_name, installed,
 *      online, hashrate_ths, power_kw}]
 *
 * OPT-IN ONLY. What leaves the device per refresh: the API key in the Authorization
 * header, over HTTPS, to the configured MMP host. No miner telemetry, no local IPs.
 * Public hosts must be HTTPS; plain HTTP only toward private/Tailscale addresses.
 */
@Singleton
class MmpClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class FleetSummary(
        val installed: Int?,
        val online: Int?,
        val healthy: Int?,
        val needsAttention: Int?,
        val zeroHash: Int?,
        val hashrateThs: Double?,
        val hashrateNominalThs: Double?,
        val realizationPct: Double?,
        val powerKw: Double?,
        val efficiencyJth: Double?,
        val availabilityPct: Double?,
        val generatedAt: String?,
    )

    data class SiteRollup(
        val siteId: String,
        val siteName: String,
        val installed: Int?,
        val online: Int?,
        val hashrateThs: Double?,
        val powerKw: Double?,
    )

    sealed interface MmpResult<out T> {
        data class Ok<T>(val value: T) : MmpResult<T>
        data class Error(val message: String) : MmpResult<Nothing>
    }

    fun validateBaseUrl(baseUrl: String): String? {
        val url = baseUrl.trim().removeSuffix("/").toHttpUrlOrNull()
            ?: return "Not a valid URL."
        if (!url.isHttps && !MinerHostValidator.resolvesToAllowed(url.host)) {
            return "Public MMP hosts must use HTTPS (plain HTTP is allowed only for private/Tailscale addresses)."
        }
        return null
    }

    suspend fun fetchFleetSummary(baseUrl: String, apiKey: String): MmpResult<FleetSummary> =
        get(baseUrl, apiKey, "/api/v1/fleet/summary") { body ->
            val o = json.parseToJsonElement(body).jsonObject
            FleetSummary(
                installed = o.int("installed"),
                online = o.int("online"),
                healthy = o.int("healthy"),
                needsAttention = o.int("needs_attention"),
                zeroHash = o.int("zero_hash"),
                hashrateThs = o.num("hashrate_ths"),
                hashrateNominalThs = o.num("hashrate_nominal_ths"),
                realizationPct = o.num("hashrate_realization_pct"),
                powerKw = o.num("power_kw"),
                efficiencyJth = o.num("efficiency_jth"),
                availabilityPct = o.num("availability_pct"),
                generatedAt = o.str("generated_at"),
            )
        }

    suspend fun fetchBySite(baseUrl: String, apiKey: String): MmpResult<List<SiteRollup>> =
        get(baseUrl, apiKey, "/api/v1/fleet/by-site") { body ->
            (json.parseToJsonElement(body) as? JsonArray)?.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                SiteRollup(
                    siteId = o.str("site_id") ?: return@mapNotNull null,
                    siteName = o.str("site_name") ?: o.str("site_id") ?: "?",
                    installed = o.int("installed"),
                    online = o.int("online"),
                    hashrateThs = o.num("hashrate_ths"),
                    powerKw = o.num("power_kw"),
                )
            }.orEmpty()
        }

    private suspend fun <T> get(
        baseUrl: String,
        apiKey: String,
        path: String,
        parse: (String) -> T,
    ): MmpResult<T> = withContext(Dispatchers.IO) {
        validateBaseUrl(baseUrl)?.let { return@withContext MmpResult.Error(it) }
        if (apiKey.isBlank()) return@withContext MmpResult.Error("No MMP API key configured.")
        val url = (baseUrl.trim().removeSuffix("/") + path).toHttpUrlOrNull()
            ?: return@withContext MmpResult.Error("Invalid request URL.")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        try {
            okHttpClient.newCall(request).execute().use { resp ->
                when {
                    resp.code == 401 -> return@withContext MmpResult.Error(
                        "MMP does not recognize this API key (401). Mint a key in the MMP admin UI."
                    )
                    resp.code == 403 -> return@withContext MmpResult.Error(
                        "MMP recognizes the key but it lacks fleet-read permission (403)."
                    )
                    !resp.isSuccessful -> return@withContext MmpResult.Error("MMP answered HTTP ${resp.code}.")
                }
                val body = resp.body?.string().orEmpty()
                runCatching { MmpResult.Ok(parse(body)) }
                    .getOrElse { MmpResult.Error("Unexpected MMP response format.") }
            }
        } catch (e: IOException) {
            MmpResult.Error(e.message ?: "Network error reaching MMP.")
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.num(key: String): Double? =
        (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun JsonObject.int(key: String): Int? = num(key)?.toInt()
}
