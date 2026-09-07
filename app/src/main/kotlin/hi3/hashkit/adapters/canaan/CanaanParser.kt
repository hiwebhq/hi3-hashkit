package hi3.hashkit.adapters.canaan

import hi3.hashkit.domain.model.FanReading
import hi3.hashkit.domain.model.MinerIdentity
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.Instant

/**
 * Parser for the Canaan Avalon CGMiner API (verified against a live Avalon Nano 3,
 * fw 24071801). Uses the `version`, `summary`, `estats`, `pools`, and `coin`
 * commands, all read-only.
 *
 * The `estats` payload's "MM ID0" field is Canaan's bracket format:
 * `Temp[52] TMax[92] Fan1[4740] FanR[59%] PS[0 0 0 4 2728 124 326] ...`
 * Observed semantics on the Nano 3 (documented in docs/DEVICE_MATRIX.md):
 *  - TMax/TAvg: hottest/average chip temperature; Temp: board/inlet sensor
 *  - Fan1: RPM, FanR: fan duty percent
 *  - PS[5]: wall power in watts (124 observed vs MPO[125] configured max — consistent);
 *    reported as REPORTED, never MEASURED, because the sensor chain is undocumented
 *  - GHSmm/GHSavg/GHSspd: board/average/current hashrate in GH/s
 *  - WORKLEVEL: current performance mode index, TA: ASIC count
 */
object CanaanParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val bracketRegex = Regex("""([A-Za-z0-9_]+)\[([^\]]*)]""")

    data class VersionInfo(
        val prod: String?,
        val model: String?,
        val firmwareVersion: String?,
        val hwType: String?,
        val apiVersion: String?,
    )

    fun parseVersion(body: String): VersionInfo? {
        val v = firstRecord(body, "VERSION") ?: return null
        val prod = v.str("PROD") ?: return null
        return VersionInfo(
            prod = prod,
            model = v.str("MODEL"),
            firmwareVersion = v.str("VERSION"),
            hwType = v.str("HWTYPE"),
            apiVersion = v.str("API"),
        )
    }

    fun isAvalon(info: VersionInfo?): Boolean =
        info?.prod?.contains("avalon", ignoreCase = true) == true

    fun identityOf(info: VersionInfo, mmFields: Map<String, String>): MinerIdentity {
        val model = when (info.model?.lowercase()) {
            "nano3" -> "Avalon Nano 3"
            "nano3s" -> "Avalon Nano 3S"
            "q" -> "Avalon Q"
            null -> info.prod
            else -> "Avalon ${info.model}"
        }
        // DNA is often zeroed; HDNA is a stable hardware id on observed firmware.
        val serial = mmFields["HDNA"]?.takeIf { it.isNotBlank() && it.any { c -> c != '0' } }
            ?: mmFields["DNA"]?.takeIf { it.isNotBlank() && it.any { c -> c != '0' } }
        return MinerIdentity(
            serialNumber = serial,
            manufacturer = "Canaan",
            model = model,
            boardVersion = info.hwType,
            asicModel = mmFields["Core"],
            firmwareFamily = "Canaan MM/cgminer",
            firmwareVersion = info.firmwareVersion,
        )
    }

    fun mmFieldsOf(estatsBody: String?): Map<String, String> {
        val stats = estatsBody?.let { firstRecord(it, "STATS") } ?: return emptyMap()
        val mm = stats.str("MM ID0") ?: return emptyMap()
        return bracketRegex.findAll(mm).associate { it.groupValues[1] to it.groupValues[2].trim() }
    }

    fun parseTelemetry(
        summaryBody: String?,
        estatsBody: String?,
        poolsBody: String?,
        coinBody: String?,
    ): MinerTelemetry {
        val summary = summaryBody?.let { firstRecord(it, "SUMMARY") }
        val mm = mmFieldsOf(estatsBody)
        val pool = activePool(poolsBody)
        val coin = coinBody?.let { firstRecord(it, "COIN") }

        // Hashrate: 5-minute window is the most representative "current" figure.
        val mhs5m = summary?.num("MHS 5m")
        val hashrateGhs = mhs5m?.div(1000.0)
            ?: mm["GHSavg"]?.toDoubleOrNull()

        val powerW = mm["PS"]?.split(Regex("\\s+"))?.getOrNull(5)?.toDoubleOrNull()
            ?.takeIf { it in 1.0..5000.0 }

        val fans = buildList {
            val rpm = mm["Fan1"]?.toDoubleOrNull()?.toInt()
            val pct = mm["FanR"]?.removeSuffix("%")?.toDoubleOrNull()?.toInt()
            if (rpm != null || pct != null) add(FanReading(0, rpm, pct))
            mm["Fan2"]?.toDoubleOrNull()?.toInt()?.let { add(FanReading(1, it, null)) }
        }

        val consumed = setOf(
            "GHSavg", "PS", "Fan1", "Fan2", "FanR", "TMax", "TAvg", "Elapsed",
            "HDNA", "DNA", "Core",
        )

        return MinerTelemetry(
            timestamp = Instant.now(),
            status = MinerStatus.ONLINE,
            hashrateGhs = Sourced.reported(hashrateGhs),
            powerW = Sourced.reported(powerW),
            efficiencyJTh = Sourced.calculated(
                hi3.hashkit.core.Units.efficiencyJTh(powerW, hashrateGhs)
            ),
            chipTempC = Sourced.measured(mm["TMax"]?.toDoubleOrNull()),
            vrTempC = Sourced.unavailable(),
            fans = fans,
            asicCount = mm["TA"]?.toDoubleOrNull()?.toInt(),
            sharesAccepted = summary?.num("Accepted")?.toLong(),
            sharesRejected = summary?.num("Rejected")?.toLong(),
            bestDifficulty = summary?.num("Best Share"),
            uptimeSeconds = (summary?.num("Elapsed") ?: mm["Elapsed"]?.toDoubleOrNull())?.toLong(),
            poolUrl = pool?.str("URL")?.removePrefix("stratum+tcp://")?.substringBeforeLast(':'),
            poolPort = pool?.str("URL")?.substringAfterLast(':')?.toIntOrNull(),
            workerName = pool?.str("User"),
            usingFallbackPool = pool?.num("POOL")?.toInt()?.let { it != 0 },
            networkDifficulty = coin?.num("Network Difficulty"),
            unrecognizedFields = mm.filterKeys { it !in consumed },
        )
    }

    // ------------------------------------------------------------------ helpers ----

    /** First record of the given section in a CGMiner JSON response. */
    private fun firstRecord(body: String, section: String): JsonObject? =
        runCatching {
            (json.parseToJsonElement(body).jsonObject[section] as? JsonArray)
                ?.firstOrNull()?.jsonObject
        }.getOrNull()

    /** The pool actually being mined: stratum-active, else priority 0, else first. */
    private fun activePool(poolsBody: String?): JsonObject? {
        val pools = poolsBody?.let {
            runCatching { (json.parseToJsonElement(it).jsonObject["POOLS"] as? JsonArray) }.getOrNull()
        } ?: return null
        val objects = pools.mapNotNull { it as? JsonObject }
        return objects.firstOrNull { it.str("Stratum Active") == "true" }
            ?: objects.firstOrNull { it.num("Priority")?.toInt() == 0 }
            ?: objects.firstOrNull()
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.num(key: String): Double? =
        (this[key] as? JsonPrimitive)?.content?.trim()?.toDoubleOrNull()
}
