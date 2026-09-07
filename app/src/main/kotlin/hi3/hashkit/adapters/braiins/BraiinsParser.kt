package hi3.hashkit.adapters.braiins

import hi3.hashkit.domain.model.FanReading
import hi3.hashkit.domain.model.MinerIdentity
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
 * Parser for Braiins OS's CGMiner-compatible TCP API (BOSer), verified live against a
 * Braiins Mini Miner BMM 100 (boser-openwrt 0.1.0, API 3.7):
 *
 *  - `version`    -> { BOSer, API } (identifies the family; no model here)
 *  - `summary`    -> MHS windows (MH/s), shares, Best Share, Elapsed
 *  - `devs`       -> per-device MHS + "Nominal MHS" (firmware's own expected hashrate)
 *  - `temps`      -> { Board, Chip } °C
 *  - `fans`       -> { RPM, Speed% }
 *  - `devdetails` -> { Model, Chips, Frequency (MHz), Voltage (V) }
 *  - `pools`      -> classic CGMiner pool records
 *
 * The BMM 100 reports no power sensor and no MAC/serial over this API: power stays
 * UNAVAILABLE (never estimated silently) and identity falls back to model+IP.
 */
object BraiinsParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun isBoser(versionBody: String): Boolean =
        firstRecord(versionBody, "VERSION")?.str("BOSer") != null

    fun identityOf(versionBody: String?, devdetailsBody: String?): MinerIdentity {
        val version = versionBody?.let { firstRecord(it, "VERSION") }
        val details = devdetailsBody?.let { firstRecord(it, "DEVDETAILS") }
        return MinerIdentity(
            manufacturer = "Braiins",
            model = details?.str("Model") ?: "Braiins OS device",
            asicModel = details?.num("Chips")?.toInt()?.let { "$it chips" },
            firmwareFamily = "Braiins OS (BOSer)",
            firmwareVersion = version?.str("BOSer"),
        )
    }

    fun parseTelemetry(
        summaryBody: String?,
        devsBody: String?,
        tempsBody: String?,
        fansBody: String?,
        devdetailsBody: String?,
        poolsBody: String?,
    ): MinerTelemetry {
        val summary = summaryBody?.let { firstRecord(it, "SUMMARY") }
        val devs = devsBody?.let { firstRecord(it, "DEVS") }
        val temps = tempsBody?.let { firstRecord(it, "TEMPS") }
        val fans = fansBody?.let { firstRecord(it, "FANS") }
        val details = devdetailsBody?.let { firstRecord(it, "DEVDETAILS") }
        val pool = activePool(poolsBody)

        // All BOSer hashrates are MH/s; canonical unit is GH/s.
        val hashrateGhs = (summary?.num("MHS 5m") ?: devs?.num("MHS 5m"))?.div(1000.0)
        val nominalGhs = devs?.num("Nominal MHS")?.div(1000.0)

        return MinerTelemetry(
            timestamp = Instant.now(),
            status = MinerStatus.ONLINE,
            hashrateGhs = Sourced.reported(hashrateGhs),
            expectedHashrateGhs = Sourced.reported(nominalGhs),
            powerW = Sourced.unavailable(),
            efficiencyJTh = Sourced.unavailable(),
            chipTempC = Sourced.measured(temps?.num("Chip")),
            vrTempC = Sourced.unavailable(),
            fans = buildList {
                val rpm = fans?.num("RPM")?.toInt()
                val pct = fans?.num("Speed")?.toInt()
                if (rpm != null || pct != null) add(FanReading(0, rpm, pct))
            },
            frequencyMhz = Sourced.reported(details?.num("Frequency")),
            coreVoltageMv = Sourced.reported(details?.num("Voltage")?.times(1000.0)),
            asicCount = details?.num("Chips")?.toInt(),
            sharesAccepted = summary?.num("Accepted")?.toLong(),
            sharesRejected = summary?.num("Rejected")?.toLong(),
            bestDifficulty = summary?.num("Best Share"),
            uptimeSeconds = summary?.num("Elapsed")?.toLong(),
            poolUrl = pool?.str("URL")
                ?.removePrefix("stratum+tcp://")?.removePrefix("stratum2+tcp://")
                ?.substringBeforeLast(':'),
            poolPort = pool?.str("URL")?.substringAfterLast(':')?.toIntOrNull(),
            workerName = pool?.str("User"),
            usingFallbackPool = pool?.num("POOL")?.toInt()?.let { it != 0 },
            unrecognizedFields = buildMap {
                temps?.num("Board")?.let { put("boardTempC", it.toString()) }
                summary?.num("MHS 24h")?.let { put("mhs24h", it.toString()) }
            },
        )
    }

    // ------------------------------------------------------------------ helpers ----

    private fun firstRecord(body: String, section: String): JsonObject? =
        runCatching {
            (json.parseToJsonElement(body).jsonObject[section] as? JsonArray)
                ?.firstOrNull()?.jsonObject
        }.getOrNull()

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
