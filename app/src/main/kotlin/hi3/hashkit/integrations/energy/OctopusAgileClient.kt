package hi3.hashkit.integrations.energy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the Octopus Energy "Agile" half-hourly electricity price (UK) from Octopus's free,
 * public, keyless rate API:
 *
 *   GET https://api.octopus.energy/v1/products/{product}/electricity-tariffs/{tariff}/standard-unit-rates/
 *
 * The tariff code encodes the region letter, e.g. E-1R-AGILE-24-04-03-C for London. Prices come
 * back in pence/kWh (inc. VAT), newest first, in 30-minute slots. No personal data is sent — this
 * is a public product endpoint. Verified against Octopus's documented v1 REST API shape.
 */
@Singleton
class OctopusAgileClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    data class AgileRate(
        val validFrom: Instant,
        val validTo: Instant,
        /** Price in pence per kWh, inclusive of VAT. */
        val pencePerKwh: Double,
    )

    sealed interface Result {
        data class Ok(val rates: List<AgileRate>) : Result
        data class Error(val message: String) : Result
    }

    val defaultProduct = DEFAULT_PRODUCT
    suspend fun fetch(region: String, product: String = DEFAULT_PRODUCT): Result =
        withContext(Dispatchers.IO) {
            val r = region.trim().uppercase()
            if (r.length != 1 || r[0] !in 'A'..'P') {
                return@withContext Result.Error("Pick a valid region letter (A–P).")
            }
            val tariff = "E-1R-$product-$r"
            val url = "$BASE/products/$product/electricity-tariffs/$tariff/standard-unit-rates/"
            runCatching {
                okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use Result.Error("Octopus returned HTTP ${resp.code}.")
                    val rates = parseRates(resp.body?.string().orEmpty())
                    if (rates.isEmpty()) Result.Error("No rates returned for region $r.")
                    else Result.Ok(rates.sortedBy { it.validFrom })
                }
            }.getOrElse { Result.Error("Couldn't reach Octopus: ${it.message}") }
        }

    companion object {
        private const val BASE = "https://api.octopus.energy/v1"
        /** Current Agile product code; overridable if Octopus rolls a new one. */
        const val DEFAULT_PRODUCT = "AGILE-24-04-03"

        /** Pure parser for the standard-unit-rates payload — no I/O, unit-tested. */
        fun parseRates(json: String): List<AgileRate> {
            // Each result object: {"value_exc_vat":..,"value_inc_vat":..,"valid_from":"..Z","valid_to":"..Z"}
            val obj = Regex(
                "\\{[^}]*?\"value_inc_vat\"\\s*:\\s*(-?[0-9.]+)[^}]*?" +
                    "\"valid_from\"\\s*:\\s*\"([^\"]+)\"[^}]*?" +
                    "\"valid_to\"\\s*:\\s*\"([^\"]*)\"[^}]*?\\}"
            )
            return obj.findAll(json).mapNotNull { m ->
                val price = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val from = runCatching { Instant.parse(m.groupValues[2]) }.getOrNull() ?: return@mapNotNull null
                val to = runCatching { Instant.parse(m.groupValues[3]) }.getOrNull() ?: from
                AgileRate(from, to, price)
            }.toList()
        }

        /** The rate whose window contains [now], or null if none covers it. */
        fun currentRate(rates: List<AgileRate>, now: Instant = Instant.now()): AgileRate? =
            rates.firstOrNull { !now.isBefore(it.validFrom) && now.isBefore(it.validTo) }

        /** Upcoming rates (window not yet ended), sorted by start. */
        fun upcoming(rates: List<AgileRate>, now: Instant = Instant.now()): List<AgileRate> =
            rates.filter { it.validTo.isAfter(now) }.sortedBy { it.validFrom }
    }
}
