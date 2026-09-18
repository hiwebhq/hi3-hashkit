package hi3.hashkit.adapters.futurebit

import hi3.hashkit.domain.model.ChainReading
import hi3.hashkit.domain.model.FanReading
import hi3.hashkit.domain.model.MinerIdentity
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.Instant

/**
 * Pure parsing of the Apollo OS GraphQL `data` object (see [ApolloAdapter.STATS_QUERY]).
 *
 * Field semantics come from the Apollo OS schema (introspected 2026-09-18) and the
 * open-source apollo-ui/apollo-api dashboard that renders them:
 *  - `Miner.stats.result.stats[]` — one record per hashboard unit; `master.intervals.int_N`
 *    are rolling windows in seconds (`bySol` = hashrate the chips actually solved, GH/s;
 *    `byPool` = hashrate credited by the pool; `chipSpeed` = chip clock, MHz).
 *  - `master.boardsW` / `boardsI` — board power (W) / current (A); `wattPerGHs` = J/GH.
 *  - `temperature.{min,avr,max}` — board temperature sensors, °C.
 *  - `fans.int_0.rpm[]` — fan tachometers.
 *  - `pool.intervals.int_0` — cumulative share counters since the miner started.
 *  - `Mcu.stats.result.stats` — the controller: hostname, uptime, network (MAC), CPU temp.
 */
object ApolloParser {

    data class Parsed(val identity: MinerIdentity, val telemetry: MinerTelemetry)

    /** Hashrate window preference: 5 min first, then 30 s, 1 h, 15 min, and since-start. */
    private val HASHRATE_WINDOWS = listOf("int_300", "int_30", "int_3600", "int_900", "int_0")

    /** Identity from an unauthenticated probe: only the family is knowable without a login. */
    fun probeIdentity(): MinerIdentity = MinerIdentity(
        manufacturer = MANUFACTURER,
        model = MODEL_UNKNOWN,
        firmwareFamily = FIRMWARE_FAMILY,
    )

    /** Identity + telemetry from the authenticated stats query, or null if no miner data is present. */
    fun parse(data: JsonObject, host: String, now: Instant = Instant.now()): Parsed? {
        val minerStats = data.obj("Miner")?.obj("stats")?.obj("result")
        val units = (minerStats?.get("stats") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val online = data.obj("Miner")?.obj("online")?.obj("result")?.obj("online")?.bool("status")
        val mcu = data.obj("Mcu")?.obj("stats")?.obj("result")?.obj("stats")
        if (units.isEmpty() && mcu == null && online == null) return null

        val mcuVersion = data.obj("Mcu")?.obj("version")?.str("result")
        val pools = (data.obj("Pool")?.obj("list")?.obj("result")?.get("pools") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }.orEmpty()
        val settings = data.obj("Settings")?.obj("read")?.obj("result")?.obj("settings")

        val agg = Aggregate().also { a -> units.forEachIndexed { i, u -> a.add(i, u) } }
        val pool = resolvePool(agg, pools)
        val fanPercent = mcu?.int("minerFanSpeed")?.takeIf { it in 0..PERCENT_MAX }
        val telemetry = MinerTelemetry(
            timestamp = now,
            status = statusOf(online, agg.hashrate),
            hashrateGhs = Sourced.reported(agg.hashrate),
            powerW = Sourced.measured(agg.power),
            efficiencyJTh = Sourced.calculated(efficiencyJTh(agg.hashrate, agg.power)),
            chipTempC = Sourced.measured(agg.chipTemp),
            fans = agg.fans.map { if (fanPercent != null) it.copy(percent = fanPercent) else it },
            perChain = agg.chains,
            frequencyMhz = Sourced.reported(agg.freq),
            coreVoltageMv = Sourced.reported(settings?.dbl("voltage")?.takeIf { it > 0 }?.let { it * MV_PER_V }),
            asicCount = agg.asics,
            sharesAccepted = agg.accepted,
            sharesRejected = agg.rejected,
            uptimeSeconds = agg.uptime ?: mcu?.str("uptime")?.toLongOrNull(),
            poolUrl = pool.host,
            poolPort = pool.port,
            workerName = pool.worker,
            unrecognizedFields = buildMap {
                settings?.str("minerMode")?.let { put("minerMode", it) }
                mcu?.int("temperature")?.let { put("mcuTemperatureC", it.toString()) }
            },
        )
        return Parsed(identityOf(units, mcu, mcuVersion, host), telemetry)
    }

    private fun statusOf(online: Boolean?, hashrate: Double?): MinerStatus = when {
        online == false -> MinerStatus.OFFLINE
        hashrate != null && hashrate > 0 -> MinerStatus.ONLINE
        else -> MinerStatus.DEGRADED
    }

    private fun efficiencyJTh(hashrateGhs: Double?, powerW: Double?): Double? =
        if (hashrateGhs != null && hashrateGhs > 0 && powerW != null) powerW / (hashrateGhs / GHS_PER_THS) else null

    /** Running totals over the hashboard units of one Apollo. */
    private class Aggregate {
        var hashrate: Double? = null
        var power: Double? = null
        var uptime: Long? = null
        var chipTemp: Double? = null
        var freq: Double? = null
        var accepted: Long? = null
        var rejected: Long? = null
        var asics: Int? = null
        val fans = mutableListOf<FanReading>()
        val chains = mutableListOf<ChainReading>()
        var poolHost: String? = null
        var poolPort: Int? = null
        var worker: String? = null

        fun add(index: Int, unit: JsonObject) {
            val master = unit.obj("master")
            val window = HASHRATE_WINDOWS.firstNotNullOfOrNull { w ->
                master?.obj("intervals")?.obj(w)?.takeIf { it.dbl("bySol") != null }
            }
            addMaster(master, window)
            val temp = unit.obj("temperature")
            addSensors(unit, temp)
            addSlot(index, unit.obj("slots")?.obj("int_0"), window, temp)
            addPool(unit.obj("pool"))
        }

        private fun addMaster(master: JsonObject?, window: JsonObject?) {
            window?.dbl("bySol")?.let { hashrate = (hashrate ?: 0.0) + it }
            window?.dbl("chipSpeed")?.takeIf { it > 0 }?.let { freq = maxOf(freq ?: 0.0, it) }
            master?.dbl("boardsW")?.takeIf { it > 0 }?.let { power = (power ?: 0.0) + it }
            master?.long("upTime")?.let { uptime = maxOf(uptime ?: 0L, it) }
        }

        private fun addSensors(unit: JsonObject, temp: JsonObject?) {
            temp?.dbl("max")?.let { chipTemp = maxOf(chipTemp ?: Double.NEGATIVE_INFINITY, it) }
            (unit.obj("fans")?.obj("int_0")?.get("rpm") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                ?.forEach { rpm -> fans += FanReading(index = fans.size, rpm = rpm, percent = null) }
        }

        private fun addSlot(index: Int, slot: JsonObject?, window: JsonObject?, temp: JsonObject?) {
            slot?.int("chips")?.let { asics = (asics ?: 0) + it }
            if (slot == null && window == null) return
            chains += ChainReading(
                index = index,
                hashrateGhs = slot?.dbl("ghs") ?: window?.dbl("bySol"),
                chipsActive = slot?.int("chips"),
                chipsTotal = slot?.int("chips"),
                chipsDead = null,
                hwErrors = slot?.dbl("errors")?.toInt(),
                tempC = slot?.dbl("temperature") ?: temp?.dbl("max"),
            )
        }

        private fun addPool(pool: JsonObject?) {
            val counters = pool?.obj("intervals")?.obj("int_0")
            counters?.dbl("sharesAccepted")?.let { accepted = (accepted ?: 0L) + it.toLong() }
            counters?.dbl("sharesRejected")?.let { rejected = (rejected ?: 0L) + it.toLong() }
            if (poolHost == null) {
                poolHost = pool?.str("host")?.takeIf { it.isNotBlank() }
                poolPort = pool?.int("port")
                worker = pool?.str("userName")?.takeIf { it.isNotBlank() }
            }
        }
    }

    private data class PoolRef(val host: String?, val port: Int?, val worker: String?)

    /**
     * The miner may point at a local proxy (solo mode via the on-board ckpool); the
     * user-facing pool is then the first enabled entry of the configured pool list.
     */
    private fun resolvePool(agg: Aggregate, pools: List<JsonObject>): PoolRef {
        val direct = agg.poolHost
        if (direct != null && !direct.isLoopback()) return PoolRef(direct, agg.poolPort, agg.worker)
        val configured = pools.filter { it.bool("enabled") == true }
            .minByOrNull { it.int("index") ?: Int.MAX_VALUE }
            ?: return PoolRef(direct, agg.poolPort, agg.worker)
        val url = configured.str("url") ?: return PoolRef(direct, agg.poolPort, agg.worker)
        val (h, p) = splitPool(url)
        return PoolRef(h, p ?: agg.poolPort, configured.str("username")?.takeIf { it.isNotBlank() } ?: agg.worker)
    }

    /** Identity from the authenticated response: MAC (stable), hostname, firmware. */
    fun identityOf(units: List<JsonObject>, mcu: JsonObject?, mcuVersion: String?, host: String): MinerIdentity {
        val network = (mcu?.get("network") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val mac = network.firstOrNull { it.str("address") == host }?.str("mac")
            ?: network.firstOrNull { !it.str("mac").isNullOrBlank() && it.str("mac") != ZERO_MAC }?.str("mac")
            ?: units.firstOrNull()?.obj("master")?.str("hwAddr")
        val minerVersion = units.firstOrNull()?.obj("versions")?.str("miner")
            ?: units.firstOrNull()?.str("version")
        return MinerIdentity(
            macAddress = mac?.takeIf { it.isNotBlank() && it != ZERO_MAC }?.lowercase(),
            serialNumber = units.firstOrNull()?.str("uuid")?.takeIf { it.isNotBlank() },
            hostname = mcu?.str("hostname")?.takeIf { it.isNotBlank() },
            manufacturer = MANUFACTURER,
            model = modelOf(units),
            firmwareFamily = FIRMWARE_FAMILY,
            firmwareVersion = listOfNotNull(
                mcuVersion?.takeIf { it.isNotBlank() }?.let { "Apollo OS $it" },
                minerVersion?.takeIf { it.isNotBlank() }?.let { "miner $it" },
            ).joinToString(", ").ifBlank { null },
        )
    }

    /**
     * The schema does not name the product. Apollo II hashboards report far more chips
     * per unit than the original Apollo BTC (Gen1/Gen2), so the count separates them.
     */
    private fun modelOf(units: List<JsonObject>): String {
        val chips = units.firstOrNull()?.obj("slots")?.obj("int_0")?.int("chips")
        val unitsSuffix = if (units.size > 1) " x${units.size}" else ""
        return when {
            chips == null -> MODEL_UNKNOWN
            chips >= APOLLO_II_MIN_CHIPS -> "Apollo II$unitsSuffix"
            else -> "Apollo BTC$unitsSuffix"
        }
    }

    private fun String.isLoopback(): Boolean =
        this == "127.0.0.1" || this.equals("localhost", ignoreCase = true) || this == "::1"

    /** `stratum+tcp://host:port` or `host:port` → (host, port?). */
    fun splitPool(url: String): Pair<String, Int?> {
        val noScheme = url.substringAfter("://")
        val hostPort = noScheme.substringBefore('/')
        val port = hostPort.substringAfterLast(':', "").toIntOrNull()
        val host = if (port != null) hostPort.substringBeforeLast(':') else hostPort
        return host to port
    }

    const val MANUFACTURER = "FutureBit"
    const val FIRMWARE_FAMILY = "Apollo OS"
    const val MODEL_UNKNOWN = "Apollo"
    private const val ZERO_MAC = "00:00:00:00:00:00"
    private const val GHS_PER_THS = 1000.0
    private const val MV_PER_V = 1000.0
    private const val PERCENT_MAX = 100
    /** Apollo II boards report dozens of chips; Apollo BTC Gen1/Gen2 report far fewer. */
    private const val APOLLO_II_MIN_CHIPS = 40
}
