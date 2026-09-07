package hi3.hashkit.adapters.cgminer

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
        // "MHS 5s"/"MHS av" are in MH/s per the cgminer API; canonical unit is GH/s.
        val hashrateGhs = (s?.num("MHS 5s") ?: s?.num("MHS av"))?.div(1000.0)
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
