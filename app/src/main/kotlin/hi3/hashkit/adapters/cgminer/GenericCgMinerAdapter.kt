package hi3.hashkit.adapters.cgminer

import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.domain.adapter.FanControl
import hi3.hashkit.domain.adapter.MinerControlAdapter
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.domain.adapter.PowerAction
import hi3.hashkit.domain.adapter.ProbeResult
import hi3.hashkit.domain.adapter.TelemetryResult
import hi3.hashkit.domain.adapter.TuneOptions
import hi3.hashkit.domain.model.Capability
import hi3.hashkit.domain.model.MinerCapabilities
import hi3.hashkit.domain.model.MinerIdentity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitoring adapter for Antminer-class cgminer firmware: stock Bitmain, VNish, and
 * LuxOS. Uses only the standard cgminer `version`/`summary`/`pools` records (verified
 * shapes), so it reports hashrate, shares, uptime and pool for any such miner.
 *
 * SCOPE: temperatures, fans, power and all controls are intentionally unsupported —
 * those live in firmware-specific fields/APIs that have not been captured and verified
 * per family. They will be added once real responses from each firmware are recorded.
 *
 * This adapter is tried after the device-specific ones and explicitly declines Avalon
 * (Canaan) and BOSer (Braiins) miners so those keep their richer adapters.
 */
@Singleton
class GenericCgMinerAdapter @Inject constructor(
    private val api: CgMinerApi,
    private val vnish: VnishWebClient,
) : MinerControlAdapter {

    override val adapterType: String = TYPE
    override val displayName: String = "Antminer-class (cgminer)"
    override val defaultPort: Int = CgMinerApi.DEFAULT_PORT

    override suspend fun probe(host: MinerHost): ProbeResult {
        val port = apiPort(host)
        val result = api.query(host.host, port, "version")
        val versionBody = result.body ?: return ProbeResult.Unreachable(result.error ?: "No response")
        // VNish's `version` errors; fall back to `stats` (its Type carries the family).
        var family = CgMinerCommon.family(versionBody)
        var statsBody: String? = null
        if (family == null || family == CgMinerCommon.Family.UNKNOWN) {
            statsBody = api.query(host.host, port, "stats").body
            family = CgMinerCommon.familyFromStats(statsBody)
        }
        // Decline anything a specific adapter owns, or that isn't recognizably cgminer.
        if (family == null || family == CgMinerCommon.Family.AVALON ||
            family == CgMinerCommon.Family.BOSER || family == CgMinerCommon.Family.UNKNOWN
        ) {
            return ProbeResult.NotThisDevice
        }
        return ProbeResult.Supported(TYPE, identity(versionBody, statsBody, family), versionBody)
    }

    override suspend fun getIdentity(host: MinerHost): MinerIdentity? =
        (probe(host) as? ProbeResult.Supported)?.identity

    override suspend fun getTelemetry(host: MinerHost): TelemetryResult {
        val port = apiPort(host)
        val versionBody = api.query(host.host, port, "version").body
        val summary = api.query(host.host, port, "summary").body
        val pools = api.query(host.host, port, "pools").body
        val statsBody = api.query(host.host, port, "stats").body
        val family = CgMinerCommon.family(versionBody ?: "")
            ?: CgMinerCommon.familyFromStats(statsBody)
        if (family == null || family == CgMinerCommon.Family.AVALON || family == CgMinerCommon.Family.BOSER) {
            return TelemetryResult.ParseError("Not an Antminer-class cgminer device", versionBody ?: statsBody ?: "")
        }
        if (summary == null && statsBody == null) return TelemetryResult.Offline("No summary/stats from cgminer API")
        var telemetry = CgMinerCommon.parseStandardTelemetry(summary, pools)
        // Bitmain & VNish: enrich with verified stats fields (temps/fans/freq/expected/power).
        if (family == CgMinerCommon.Family.ANTMINER_STOCK || family == CgMinerCommon.Family.VNISH) {
            telemetry = CgMinerCommon.enrichWithStats(telemetry, statsBody)
        }
        val raw = buildString {
            append("{")
            append("\"version\":").append(versionBody ?: "null")
            summary?.let { append(",\"summary\":").append(it) }
            pools?.let { append(",\"pools\":").append(it) }
            statsBody?.let { append(",\"stats\":").append(it) }
            append("}")
        }
        return TelemetryResult.Success(telemetry, raw)
    }

    override fun getCapabilities(identity: MinerIdentity?): MinerCapabilities {
        // VNish exposes an authenticated web API for reboot + pause/resume; stock Bitmain
        // and LuxOS have no verified control path, so they stay monitoring-only.
        val isVnish = identity?.firmwareFamily?.contains("vnish", ignoreCase = true) == true
        if (!isVnish) {
            return MinerCapabilities(
                supported = setOf(Capability.TELEMETRY),
                unsupportedReasons = Capability.entries
                    .filter { it != Capability.TELEMETRY }
                    .associateWith {
                        "Controls for this firmware are not yet verified — monitoring shows " +
                            "hashrate, shares, uptime, pool and (where reported) temps/fans/power."
                    },
            )
        }
        return MinerCapabilities(
            supported = setOf(Capability.TELEMETRY, Capability.REBOOT, Capability.POWER_CONTROL),
            unsupportedReasons = mapOf(
                Capability.SET_POOLS to "VNish pool changes require its full settings-object " +
                    "round-trip, which isn't verified yet.",
                Capability.SET_FAN to "VNish fan/preset changes go through its settings object, not yet verified.",
                Capability.APPLY_APPROVED_TUNE to "VNish tuning goes through its autotune presets, not yet verified.",
                Capability.LOGS to "No log stream over the cgminer API.",
            ),
        )
    }

    // --- controls (VNish web API only; other cgminer firmware stays monitoring-only) ------

    private fun requireSecret(host: MinerHost): String? = host.secret?.takeIf { it.isNotBlank() }

    override suspend fun reboot(host: MinerHost): ActionResult {
        val pw = requireSecret(host)
            ?: return ActionResult.Unsupported("Set the VNish web password in the miner's settings first.")
        return vnish.reboot(host.host, pw)
    }

    override suspend fun powerControl(host: MinerHost, action: PowerAction): ActionResult {
        val pw = requireSecret(host)
            ?: return ActionResult.Unsupported("Set the VNish web password in the miner's settings first.")
        return vnish.pauseResume(host.host, pw, pause = action == PowerAction.PAUSE)
    }

    override suspend fun getTuneOptions(host: MinerHost): TuneOptions? = null

    override suspend fun setPrimaryPool(host: MinerHost, url: String, port: Int, worker: String): ActionResult =
        ActionResult.Unsupported("VNish pool changes need its settings-object API (not verified).")

    override suspend fun setFan(host: MinerHost, config: FanControl): ActionResult =
        ActionResult.Unsupported("VNish fan control needs its settings-object API (not verified).")

    override suspend fun applyTune(host: MinerHost, frequencyMhz: Int, coreVoltageMv: Int): ActionResult =
        ActionResult.Unsupported("VNish tuning needs its autotune-preset API (not verified).")

    private fun identity(
        versionBody: String,
        statsBody: String?,
        family: CgMinerCommon.Family,
    ): MinerIdentity {
        val v = CgMinerCommon.firstRecord(versionBody, "VERSION")
        // VNish carries model/fw in the stats record's Type, not version.
        val statsType = statsBody?.let { CgMinerCommon.familyModelFromStats(it) }
        fun s(o: kotlinx.serialization.json.JsonObject?, k: String) =
            (o?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content
        return MinerIdentity(
            manufacturer = CgMinerCommon.familyLabel(family),
            model = s(v, "Type") ?: statsType,
            firmwareFamily = CgMinerCommon.familyLabel(family),
            firmwareVersion = s(v, "CGMiner") ?: s(v, "LUXminer") ?: statsType,
        )
    }

    private fun apiPort(host: MinerHost): Int =
        if (host.port == 0 || host.port == 80) CgMinerApi.DEFAULT_PORT else host.port

    companion object {
        const val TYPE = "cgminer-generic"
    }
}
