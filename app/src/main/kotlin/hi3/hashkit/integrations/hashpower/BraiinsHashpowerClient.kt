package hi3.hashkit.integrations.hashpower

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only client for the Braiins Hashpower spot market, using its public (keyless) endpoints —
 * verified against the live API and the docs at academy.braiins.com/braiins-hashpower/api:
 *   GET https://hashpower.braiins.com/v1/spot/stats     — best bid/ask, last price, hashrate, volume
 *   GET https://hashpower.braiins.com/v1/spot/orderbook — asks[] (price_sat + hr_available_ph)
 *
 * No account, no API key, no personal data — just the public market shown to feature the product.
 * Renting itself (which moves funds) is never done in-app; the UI links out to Braiins.
 */
@Singleton
class BraiinsHashpowerClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    /** Prices are in sats; hashrate in PH/s; see the docs for the /day pricing basis. */
    data class Market(
        val bestAskSat: Long?,
        val bestBidSat: Long?,
        val lastAvgPriceSat: Long?,
        val volume24h: Double?,
        val availablePh: Double?,
        val matchedPh: Double?,
    )

    data class Ask(val priceSat: Long, val availablePh: Double)

    sealed interface Result {
        data class Ok(val market: Market, val asks: List<Ask>) : Result
        data class Error(
            val message: String,
            /** Last good snapshot from this app session, if any — the UI shows it stale-stamped. */
            val cached: Ok? = null,
            val cachedAtEpochMs: Long = 0,
        ) : Result
    }

    /** Overridable for tests (MockWebServer); production always uses the real endpoint. */
    internal var baseUrl: String = BASE

    @Volatile private var lastOk: Result.Ok? = null

    @Volatile private var lastOkAtEpochMs: Long = 0

    suspend fun fetch(): Result = when (val r = fetchLive()) {
        is Result.Ok -> {
            lastOk = r
            lastOkAtEpochMs = System.currentTimeMillis()
            r
        }
        is Result.Error -> r.copy(cached = lastOk, cachedAtEpochMs = lastOkAtEpochMs)
    }

    private suspend fun fetchLive(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val statsBody = get("$baseUrl/spot/stats")
                ?: return@withContext Result.Error("Couldn't reach Braiins Hashpower.")
            val stats = json.decodeFromString<StatsDto>(statsBody)
            val asks = get("$baseUrl/spot/orderbook")?.let {
                runCatching { json.decodeFromString<OrderbookDto>(it).asks }.getOrNull()
            }.orEmpty()
                .mapNotNull { a ->
                    val p = a.priceSat ?: return@mapNotNull null
                    Ask(Math.round(p), a.availablePh ?: 0.0)
                }
                .sortedBy { it.priceSat }
            Result.Ok(
                Market(
                    bestAskSat = stats.bestAskSat?.let(Math::round),
                    bestBidSat = stats.bestBidSat?.let(Math::round),
                    lastAvgPriceSat = stats.lastAvgPriceSat?.let(Math::round),
                    volume24h = stats.volume24h,
                    availablePh = stats.availablePh,
                    matchedPh = stats.matchedPh,
                ),
                asks,
            )
        }.getOrElse { Result.Error("Braiins Hashpower error: ${it.message}") }
    }

    private fun get(url: String): String? =
        runCatching {
            okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()

    // Sat prices are decoded as Double: the API returns them as integers or decimals
    // (last_avg_price_sat went fractional 2026-09) — they're rounded to whole sats above.
    @Serializable
    private data class StatsDto(
        @SerialName("best_ask_sat") val bestAskSat: Double? = null,
        @SerialName("best_bid_sat") val bestBidSat: Double? = null,
        @SerialName("last_avg_price_sat") val lastAvgPriceSat: Double? = null,
        @SerialName("volume_24h_m") val volume24h: Double? = null,
        @SerialName("hash_rate_available_10m_ph") val availablePh: Double? = null,
        @SerialName("hash_rate_matched_10m_ph") val matchedPh: Double? = null,
    )

    @Serializable
    private data class OrderbookDto(val asks: List<AskDto> = emptyList())

    @Serializable
    private data class AskDto(
        @SerialName("price_sat") val priceSat: Double? = null,
        @SerialName("hr_available_ph") val availablePh: Double? = null,
    )

    companion object {
        private const val BASE = "https://hashpower.braiins.com/v1"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
