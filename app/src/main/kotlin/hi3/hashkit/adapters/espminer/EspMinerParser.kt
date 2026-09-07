package hi3.hashkit.adapters.espminer

import hi3.hashkit.core.Units
import hi3.hashkit.domain.model.FanReading
import hi3.hashkit.domain.model.MinerIdentity
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.Instant

/**
 * Tolerant parser for ESP-Miner / AxeOS `GET /api/system/info`.
 *
 * The endpoint and field names come from the open-source ESP-Miner firmware
 * (github.com/bitaxeorg/ESP-Miner). Firmware versions vary: fields may be missing,
 * null, renamed, or numbers may arrive as strings ("bestDiff": "4.29M"). Everything
 * is extracted defensively from a JsonObject; unrecognized fields are preserved for
 * diagnostics rather than dropped.
 */
object EspMinerParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Field names this parser consumes; everything else lands in unrecognizedFields. */
    private val KNOWN_KEYS = setOf(
        "power", "voltage", "current", "temp", "temp2", "vrTemp", "maxPower", "minPower",
        "hashRate", "hashRate_10m", "hashRate_1h", "hashRate_1d", "expectedHashrate",
        "bestDiff", "bestSessionDiff", "freeHeap", "coreVoltage", "coreVoltageActual",
        "frequency", "ssid", "macAddr", "hostname", "wifiStatus", "wifiRSSI",
        "sharesAccepted", "sharesRejected", "uptimeSeconds", "asicCount", "smallCoreCount",
        "ASICModel", "stratumURL", "stratumPort", "stratumUser",
        "fallbackStratumURL", "fallbackStratumPort", "fallbackStratumUser",
        "isUsingFallbackStratum", "version", "idfVersion", "boardVersion", "runningPartition",
        "flipscreen", "overheat_mode", "invertscreen", "invertfanpolarity",
        "autofanspeed", "fanspeed", "fanrpm", "fanrpm2",
        // ESP-Miner forks and derivatives (Lucky Miner-style, NerdQAxe)
        "DeviceModel", "deviceModel", "sn_str",
    )

    fun parseSystemInfo(body: String): ParsedSystemInfo? {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        if (obj.isEmpty()) return null

        val identity = MinerIdentity(
            macAddress = obj.str("macAddr"),
            serialNumber = obj.str("sn_str"),
            hostname = obj.str("hostname"),
            manufacturer = "Bitaxe / ESP-Miner",
            model = obj.str("DeviceModel") ?: obj.str("deviceModel")
                ?: obj.str("ASICModel")?.let { asic ->
                    obj.str("boardVersion")?.let { "Bitaxe $it ($asic)" } ?: "Bitaxe ($asic)"
                } ?: obj.str("boardVersion")?.let { "Bitaxe $it" },
            boardVersion = obj.str("boardVersion"),
            asicModel = obj.str("ASICModel"),
            firmwareFamily = "ESP-Miner/AxeOS",
            firmwareVersion = obj.str("version"),
        )

        val hashrate = obj.num("hashRate")
        // Bitaxe measures power with an onboard power sensor (INA260/TPS546 depending on board).
        val power = obj.num("power")
        val expected = obj.num("expectedHashrate")

        val fans = buildList {
            val rpm = obj.num("fanrpm")?.toInt()
            val pct = obj.num("fanspeed")?.toInt()
            if (rpm != null || pct != null) add(FanReading(index = 0, rpm = rpm, percent = pct))
            obj.num("fanrpm2")?.toInt()?.let { add(FanReading(index = 1, rpm = it, percent = null)) }
        }

        val telemetry = MinerTelemetry(
            timestamp = Instant.now(),
            status = MinerStatus.ONLINE,
            hashrateGhs = Sourced.reported(hashrate),
            expectedHashrateGhs = Sourced.reported(expected),
            powerW = Sourced.measured(power),
            efficiencyJTh = Sourced.calculated(Units.efficiencyJTh(power, hashrate)),
            chipTempC = Sourced.measured(obj.num("temp")),
            vrTempC = Sourced.measured(obj.num("vrTemp")),
            fans = fans,
            autoFanEnabled = obj.bool("autofanspeed"),
            frequencyMhz = Sourced.reported(obj.num("frequency")),
            coreVoltageMv = Sourced.reported(obj.num("coreVoltageActual") ?: obj.num("coreVoltage")),
            inputVoltageMv = Sourced.measured(obj.num("voltage")),
            asicCount = obj.num("asicCount")?.toInt(),
            sharesAccepted = obj.num("sharesAccepted")?.toLong(),
            sharesRejected = obj.num("sharesRejected")?.toLong(),
            bestDifficulty = obj.difficulty("bestDiff"),
            bestSessionDifficulty = obj.difficulty("bestSessionDiff"),
            uptimeSeconds = obj.num("uptimeSeconds")?.toLong(),
            poolUrl = obj.str("stratumURL"),
            poolPort = obj.num("stratumPort")?.toInt(),
            workerName = obj.str("stratumUser"),
            usingFallbackPool = obj.bool("isUsingFallbackStratum"),
            unrecognizedFields = obj.filterKeys { it !in KNOWN_KEYS }
                .mapValues { (_, v) -> v.toString() },
        )

        return ParsedSystemInfo(identity, telemetry)
    }

    // --- defensive extraction helpers -------------------------------------------------

    private fun JsonObject.prim(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonObject.str(key: String): String? =
        prim(key)?.takeIf { it.isString || it.content.isNotBlank() }
            ?.content?.takeIf { it.isNotBlank() && it != "null" }

    /** Number that may arrive as a JSON number or a numeric string. */
    private fun JsonObject.num(key: String): Double? =
        prim(key)?.content?.trim()?.toDoubleOrNull()

    private fun JsonObject.bool(key: String): Boolean? = when (prim(key)?.content?.trim()?.lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }

    /** Difficulty that may be numeric or suffixed ("4.29M", "1.2G"). */
    private fun JsonObject.difficulty(key: String): Double? {
        val raw = prim(key)?.content?.trim() ?: return null
        raw.toDoubleOrNull()?.let { return it }
        if (raw.isEmpty()) return null
        val suffix = raw.last().uppercaseChar()
        val mult = when (suffix) {
            'K' -> 1e3
            'M' -> 1e6
            'G' -> 1e9
            'T' -> 1e12
            'P' -> 1e15
            'E' -> 1e18
            else -> return null
        }
        return raw.dropLast(1).trim().toDoubleOrNull()?.times(mult)
    }
}

data class ParsedSystemInfo(
    val identity: MinerIdentity,
    val telemetry: MinerTelemetry,
)
