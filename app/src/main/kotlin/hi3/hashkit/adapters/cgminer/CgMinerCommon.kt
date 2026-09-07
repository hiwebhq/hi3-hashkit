package hi3.hashkit.adapters.cgminer

import hi3.hashkit.domain.model.FanReading
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.Instant

/**
 * Parsing for the STANDARD cgminer API commands (`version`, `summary`, `pools`) whose
 * field names are common across cgminer and its Antminer-class derivatives (stock
 * Bitmain, VNish, LuxOS). These shapes were verified live against real Avalon and
 * Braiins units, which use the same standard records.
 *
 * Firmware-specific data (per-board temps, fans, power, controls) is deliberately NOT
 * parsed here — those fields differ by firmware and must be verified per family before
 * they are surfaced. The generic adapter reports them as unavailable.
 */
object CgMinerCommon {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    enum class Family { ANTMINER_STOCK, VNISH, LUXOS, GENERIC, AVALON, BOSER, UNKNOWN }

    /** Identify the firmware family from a `version` response, or null if not cgminer. */
    fun family(versionBody: String): Family? {
        val lower = versionBody.lowercase()
        val v = firstRecord(versionBody, "VERSION") ?: return null
        return when {
            "avalon" in lower || v.str("PROD")?.contains("avalon", true) == true -> Family.AVALON
            "boser" in lower -> Family.BOSER
            "luxminer" in lower || "luxos" in lower || "luxcore" in lower -> Family.LUXOS
            "vnish" in lower -> Family.VNISH
            v.str("Type")?.contains("antminer", true) == true -> Family.ANTMINER_STOCK
            v.str("CGMiner") != null || v.str("API") != null -> Family.GENERIC
            else -> Family.UNKNOWN
        }
    }

    /** VNish's `version` errors, but its `stats` Type carries "Vnish"/"Antminer". */
    fun familyFromStats(statsBody: String?): Family? {
        val type = statsBody?.let {
            runCatching {
                (json.parseToJsonElement(it).jsonObject["STATS"] as? JsonArray)
                    ?.mapNotNull { e -> e as? JsonObject }
                    ?.firstNotNullOfOrNull { o -> o.str("Type") }
            }.getOrNull()
        } ?: return null
        val lower = type.lowercase()
        return when {
            "vnish" in lower -> Family.VNISH
            "luxos" in lower || "luxminer" in lower -> Family.LUXOS
            "antminer" in lower -> Family.ANTMINER_STOCK
            else -> Family.GENERIC
        }
    }

    /** The stats-record Type string (e.g. "Antminer S21 Pro (Vnish 1.3.4)"), if present. */
    fun familyModelFromStats(statsBody: String?): String? =
        statsBody?.let {
            runCatching {
                (json.parseToJsonElement(it).jsonObject["STATS"] as? JsonArray)
                    ?.mapNotNull { e -> e as? JsonObject }
                    ?.firstNotNullOfOrNull { o -> o.str("Type") }
            }.getOrNull()
        }

    fun familyLabel(f: Family): String = when (f) {
        Family.ANTMINER_STOCK -> "Bitmain (stock cgminer)"
        Family.VNISH -> "VNish"
        Family.LUXOS -> "LuxOS"
        Family.GENERIC -> "cgminer"
        else -> "cgminer"
    }

    /** Standard telemetry: hashrate/shares/uptime/pool only. Temps/fans/power stay unavailable. */
    fun parseStandardTelemetry(summaryBody: String?, poolsBody: String?): MinerTelemetry {
        val s = summaryBody?.let { firstRecord(it, "SUMMARY") }
        val pool = activePool(poolsBody)
        val hashrateGhs = summaryHashrateGhs(s)
        return MinerTelemetry(
            timestamp = Instant.now(),
            status = MinerStatus.ONLINE,
            hashrateGhs = Sourced.reported(hashrateGhs),
            sharesAccepted = s?.num("Accepted")?.toLong(),
            sharesRejected = s?.num("Rejected")?.toLong(),
            bestDifficulty = s?.num("Best Share"),
            uptimeSeconds = s?.num("Elapsed")?.toLong(),
            poolUrl = pool?.str("URL")
                ?.substringAfter("://")?.substringBeforeLast(':')?.ifBlank { null },
            poolPort = pool?.str("URL")?.substringAfterLast(':')?.toIntOrNull(),
            workerName = pool?.str("User"),
        )
    }

    /** Summary hashrate in GH/s: Bitmain reports GHS directly; others report MHS. */
    private fun summaryHashrateGhs(s: JsonObject?): Double? {
        if (s == null) return null
        s.num("GHS 5s")?.let { return it }
        s.num("GHS av")?.let { return it }
        return (s.num("MHS 5s") ?: s.num("MHS av"))?.div(1000.0)
    }

    /**
     * Enrich telemetry with temps/fans/frequency/expected-hashrate from an Antminer-class
     * `stats` record. Verified against:
     *  - stock Bitmain (Antminer S21 Pro, BMMiner 1.0.0): chip temps in temp2_* /
     *    temp_chip*, fans fan1..fan_num, expected in total_rateideal, freq `frequency`.
     *    No power in the API → stays unavailable.
     *  - VNish (Antminer S21 Pro, Vnish 1.3.4): same temp/fan/rateideal layout, freq in
     *    freq_avg*, plus per-chain wall power in chain_consumption* → summed and reported.
     */
    fun enrichWithStats(base: MinerTelemetry, statsBody: String?): MinerTelemetry {
        val stats = statsBody?.let {
            runCatching { (json.parseToJsonElement(it).jsonObject["STATS"] as? JsonArray) }
                .getOrNull()?.mapNotNull { e -> e as? JsonObject }
                ?.firstOrNull { o -> o["GHS 5s"] != null || o["temp_num"] != null || o["total_acn"] != null }
        } ?: return base

        val chipTemps = buildList {
            for (i in 1..8) stats.num("temp2_$i")?.let { add(it) }
            for (i in 1..8) stats.num("temp3_$i")?.let { add(it) }
            for (i in 1..8) stats.str("temp_chip$i")?.split("-")?.forEach { it.trim().toDoubleOrNull()?.let(::add) }
        }.filter { it > 0 }
        val boardTemps = buildList { for (i in 1..8) stats.num("temp$i")?.let { add(it) } }.filter { it > 0 }
        val fans = buildList {
            val n = stats.num("fan_num")?.toInt() ?: 8
            for (i in 1..n.coerceAtMost(8)) stats.num("fan$i")?.toInt()?.takeIf { it > 0 }?.let { add(FanReading(i - 1, it, null)) }
        }
        // VNish reports per-chain wall power; Bitmain does not.
        val powerW = (1..16).sumOf { stats.num("chain_consumption$it") ?: 0.0 }.takeIf { it > 0 }
        // Frequency: Bitmain `frequency`, else average of VNish freq_avg*.
        val freqs = (1..16).mapNotNull { stats.num("freq_avg$it") }
        val frequency = stats.num("frequency") ?: freqs.average().takeIf { freqs.isNotEmpty() }
        val hashrateGhs = stats.num("GHS 5s") ?: stats.num("GHS av") ?: stats.num("total_rate")
            ?: base.hashrateGhs.value

        return base.copy(
            hashrateGhs = Sourced.reported(hashrateGhs),
            expectedHashrateGhs = Sourced.reported(stats.num("total_rateideal")),
            powerW = if (powerW != null) Sourced.reported(powerW) else base.powerW,
            efficiencyJTh = Sourced.calculated(
                hi3.hashkit.core.Units.efficiencyJTh(powerW, hashrateGhs)
            ),
            chipTempC = Sourced.measured(chipTemps.maxOrNull()),
            frequencyMhz = Sourced.reported(frequency),
            asicCount = stats.num("total_acn")?.toInt(),
            fans = fans.ifEmpty { base.fans },
            unrecognizedFields = base.unrecognizedFields + buildMap {
                boardTemps.maxOrNull()?.let { put("boardTempC", it.toString()) }
                stats.str("Type")?.let { put("model", it) }
            },
        )
    }

    // ------------------------------------------------------------------ helpers ----

    fun firstRecord(body: String, section: String): JsonObject? =
        runCatching {
            (json.parseToJsonElement(body).jsonObject[section] as? JsonArray)
                ?.firstOrNull()?.jsonObject
        }.getOrNull()

    private fun activePool(poolsBody: String?): JsonObject? {
        val pools = poolsBody?.let {
            runCatching { (json.parseToJsonElement(it).jsonObject["POOLS"] as? JsonArray) }.getOrNull()
        } ?: return null
        val objs = pools.mapNotNull { it as? JsonObject }
        return objs.firstOrNull { it.str("Stratum Active") == "true" }
            ?: objs.firstOrNull { it.num("Priority")?.toInt() == 0 }
            ?: objs.firstOrNull()
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.num(key: String): Double? =
        (this[key] as? JsonPrimitive)?.content?.trim()?.toDoubleOrNull()
}
