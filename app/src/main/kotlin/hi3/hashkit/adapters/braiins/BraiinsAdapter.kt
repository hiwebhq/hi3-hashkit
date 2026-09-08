package hi3.hashkit.adapters.braiins

import hi3.hashkit.adapters.cgminer.CgMinerApi
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
 * Adapter for Braiins OS devices via the BOSer CGMiner-compatible TCP API (verified live:
 * Braiins Mini Miner BMM 100).
 *
 * MONITORING + verified Pause/Resume. BOSminer's API exposes `{"command":"pause"}` /
 * `{"command":"resume"}` (documented by Braiins: "immediately pause mining and stop power
 * consumption, prepare for resume" / "restart mining after pause") — reversible and
 * parameterless, so it maps onto POWER_CONTROL. Pool edits are intentionally NOT exposed:
 * Braiins documents switchpool/enablepool as "not fully implemented" (reset after restart),
 * and reboot/fan/tuning live in the gRPC/web APIs which are not yet verified.
 */
@Singleton
class BraiinsAdapter @Inject constructor(
    private val api: CgMinerApi,
) : MinerControlAdapter {

    override val adapterType: String = TYPE
    override val displayName: String = "Braiins OS"
    override val defaultPort: Int = CgMinerApi.DEFAULT_PORT

    override suspend fun probe(host: MinerHost): ProbeResult {
        val port = apiPort(host)
        val result = api.query(host.host, port, "version")
        val body = result.body
            ?: return ProbeResult.Unreachable(result.error ?: "No response")
        if (!BraiinsParser.isBoser(body)) return ProbeResult.NotThisDevice
        val details = api.query(host.host, port, "devdetails").body
        return ProbeResult.Supported(
            adapterType = TYPE,
            identity = BraiinsParser.identityOf(body, details),
            rawResponse = body,
        )
    }

    override suspend fun getIdentity(host: MinerHost): MinerIdentity? =
        (probe(host) as? ProbeResult.Supported)?.identity

    override suspend fun getTelemetry(host: MinerHost): TelemetryResult {
        val port = apiPort(host)
        val version = api.query(host.host, port, "version")
        val versionBody = version.body
            ?: return TelemetryResult.Offline(version.error ?: "No response")
        if (!BraiinsParser.isBoser(versionBody)) {
            return TelemetryResult.ParseError("Host is not a Braiins OS device", versionBody)
        }
        val summary = api.query(host.host, port, "summary").body
        val devs = api.query(host.host, port, "devs").body
        val temps = api.query(host.host, port, "temps").body
        val fans = api.query(host.host, port, "fans").body
        val details = api.query(host.host, port, "devdetails").body
        val pools = api.query(host.host, port, "pools").body
        if (summary == null && devs == null) {
            return TelemetryResult.Offline("Device answered version but not summary/devs")
        }
        val telemetry = BraiinsParser.parseTelemetry(summary, devs, temps, fans, details, pools)
        val raw = buildString {
            append("{\"version\":").append(versionBody)
            summary?.let { append(",\"summary\":").append(it) }
            devs?.let { append(",\"devs\":").append(it) }
            temps?.let { append(",\"temps\":").append(it) }
            fans?.let { append(",\"fans\":").append(it) }
            details?.let { append(",\"devdetails\":").append(it) }
            pools?.let { append(",\"pools\":").append(it) }
            append("}")
        }
        return TelemetryResult.Success(telemetry, raw)
    }

    override fun getCapabilities(identity: MinerIdentity?): MinerCapabilities =
        MinerCapabilities(
            supported = setOf(Capability.TELEMETRY, Capability.POWER_CONTROL),
            unsupportedReasons = mapOf(
                Capability.SET_POOLS to "Braiins documents its cgminer pool commands as not " +
                    "fully implemented (they reset after restart), so pool changes need its " +
                    "gRPC/web API — not yet verified.",
                Capability.REBOOT to "Device reboot isn't exposed over the BOSer cgminer API " +
                    "(only in the gRPC/web API, not yet verified).",
                Capability.SET_FAN to "Fan control lives in the Braiins gRPC/web API, not yet verified.",
                Capability.APPLY_APPROVED_TUNE to "Autotuning lives in the Braiins gRPC/web API, not yet verified.",
                Capability.LOGS to "Braiins OS does not expose a log stream over this API.",
            ),
        )

    // --- controls ---------------------------------------------------------------------

    /** BOSminer pause/resume — reversible, parameterless, verified from Braiins' API docs. */
    override suspend fun powerControl(host: MinerHost, action: PowerAction): ActionResult {
        val cmd = if (action == PowerAction.PAUSE) "pause" else "resume"
        return resultOf(api.query(host.host, apiPort(host), cmd))
    }

    override suspend fun getTuneOptions(host: MinerHost): TuneOptions? = null

    override suspend fun reboot(host: MinerHost): ActionResult =
        ActionResult.Unsupported("Reboot is not exposed over the BOSer cgminer API.")

    override suspend fun setPrimaryPool(host: MinerHost, url: String, port: Int, worker: String): ActionResult =
        ActionResult.Unsupported("Braiins pool changes need its gRPC/web API (not verified).")

    override suspend fun setFan(host: MinerHost, config: FanControl): ActionResult =
        ActionResult.Unsupported("Braiins fan control needs its gRPC/web API (not verified).")

    override suspend fun applyTune(host: MinerHost, frequencyMhz: Int, coreVoltageMv: Int): ActionResult =
        ActionResult.Unsupported("Braiins tuning needs its gRPC/web API (not verified).")

    /** BOSminer answers with a STATUS record; "E"/"F" is a rejection, anything else is success. */
    private fun resultOf(res: CgMinerApi.CgResult): ActionResult {
        val body = res.body ?: return ActionResult.Failure(res.error ?: "No response from miner")
        val status = Regex("\"STATUS\"\\s*:\\s*\"(\\w)\"").find(body)?.groupValues?.get(1)
        val msg = Regex("\"Msg\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
        return if (status == "E" || status == "F") {
            ActionResult.Failure(msg ?: "Miner rejected the command")
        } else {
            ActionResult.Success
        }
    }

    private fun apiPort(host: MinerHost): Int =
        if (host.port == 0 || host.port == 80) CgMinerApi.DEFAULT_PORT else host.port

    companion object {
        const val TYPE = "braiins-boser"
    }
}
