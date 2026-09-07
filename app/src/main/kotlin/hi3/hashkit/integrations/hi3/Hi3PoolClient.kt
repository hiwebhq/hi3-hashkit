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

    /**
     * Fetch a normalized per-worker account for any supported pool. Endpoints verified
     * against each pool's real API (public-pool source, ckpool user JSON, OCEAN's
     * documented api.ocean.xyz used by the DeepSea dashboard).
     */
    suspend fun fetchAccountFor(
        poolType: PoolType,
        baseUrl: String,
        identifier: String,
    ): PoolResult<PoolAccount> = when (poolType) {
        PoolType.HI3, PoolType.PUBLIC_POOL -> fetchAccount(baseUrl, identifier)
        PoolType.CKPOOL -> fetchCkpoolAccount(baseUrl, identifier)
        PoolType.OCEAN -> fetchOceanAccount(baseUrl, identifier)
    }

    /** ckpool: raw.stats.ckpool.org/users/{address}; hashrates are suffix strings ("1.5T"). */
    private suspend fun fetchCkpoolAccount(baseUrl: String, address: String): PoolResult<PoolAccount> {
        val addr = address.trim()
        if (addr.isEmpty()) return PoolResult.Error("No address configured.")
        return get(baseUrl, "/users/$addr") { body ->
            val obj = json.parseToJsonElement(body).jsonObject
            val workers = (obj["worker"] as? JsonArray)?.mapNotNull { el ->
                val w = el as? JsonObject ?: return@mapNotNull null
                val name = w.str("workername") ?: return@mapNotNull null
                PoolWorker(
                    sessionId = null,
                    // ckpool worker names are "address.rig"; keep the rig suffix as the label.
                    name = name.substringAfter('.', name),
                    bestDifficulty = w.num("bestever") ?: w.num("bestshare"),
                    hashRateGhs = ckHashToGhs(w.str("hashrate1hr") ?: w.str("hashrate5m") ?: w.str("hashrate1m")),
                    startTime = null,
                    lastSeen = w.num("lastshare")?.toLong()?.toString(),
                )
            }.orEmpty()
            PoolAccount(
                workersCount = (obj["workers"] as? JsonPrimitive)?.content?.toIntOrNull() ?: workers.size,
                workers = workers,
                totalHashRateGhs = ckHashToGhs(obj.str("hashrate1hr") ?: obj.str("hashrate5m"))
                    ?: workers.sumOf { it.hashRateGhs ?: 0.0 },
            )
        }
    }

    /** OCEAN: api.ocean.xyz/v1/user_hashrate_full/{address}; per-worker hashrate in H/s. */
    private suspend fun fetchOceanAccount(baseUrl: String, address: String): PoolResult<PoolAccount> {
        val addr = address.trim()
        if (addr.isEmpty()) return PoolResult.Error("No address configured.")
        return get(baseUrl, "/v1/user_hashrate_full/$addr") { body ->
            val root = json.parseToJsonElement(body).jsonObject
            // OCEAN wraps the payload under "result"/"data" on some versions; tolerate both.
            val data = (root["result"] as? JsonObject) ?: (root["data"] as? JsonObject) ?: root
            val workersEl = data["workers"]
            val workerObjs: List<Pair<String?, JsonObject>> = when (workersEl) {
                is JsonArray -> workersEl.mapNotNull { (it as? JsonObject)?.let { o -> null to o } }
                is JsonObject -> workersEl.entries.mapNotNull { e -> (e.value as? JsonObject)?.let { e.key to it } }
                else -> emptyList()
            }
            val workers = workerObjs.mapNotNull { (key, w) ->
                val name = w.str("workername") ?: w.str("name") ?: key ?: return@mapNotNull null
                PoolWorker(
                    sessionId = null,
                    name = name.substringAfter('.', name),
                    bestDifficulty = null,
                    // Values are H/s; normalize to GH/s. Prefer the shortest window present.
                    hashRateGhs = (w.num("hashrate_60s") ?: w.num("hashrate_3600") ?: w.num("hashrate_10800"))
                        ?.div(1e9),
                    startTime = null,
                    lastSeen = null,
                )
            }
            PoolAccount(
                workersCount = workers.size,
                workers = workers,
                totalHashRateGhs = workers.sumOf { it.hashRateGhs ?: 0.0 },
            )
        }
    }

    private fun ckHashToGhs(raw: String?): Double? = parseCkHashToGhs(raw)

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

    companion object {
        /**
         * Parse a ckpool suffix-encoded hashrate string (H/s, e.g. "1.5T", "500G", "0")
         * to GH/s. ckpool encodes worker hashrates as a number with a K/M/G/T/P/E suffix.
         */
        fun parseCkHashToGhs(raw: String?): Double? {
            val t = raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
            if (t == "0") return 0.0
            val mult = when (t.last().uppercaseChar()) {
                'K' -> 1e3; 'M' -> 1e6; 'G' -> 1e9; 'T' -> 1e12; 'P' -> 1e15; 'E' -> 1e18; else -> 1.0
            }
            val num = (if (t.last().isLetter()) t.dropLast(1) else t).toDoubleOrNull() ?: return null
            return num * mult / 1e9
        }
    }
}
