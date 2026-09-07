package hi3.hashkit.adapters.canaan

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
 * Adapter for Canaan Avalon home miners (Nano 3 verified live; Nano 3S and Avalon Q
 * identify through the same CGMiner API and are accepted when their `version` response
 * is demonstrably compatible).
 *
 * Controls use the CGMiner `ascset` API on port 4028 — verified against a real Nano 3,
 * whose firmware advertised its own valid options (loop, pdelay, frequency, led,
 * hashpower, fan-spd, factory, reboot, softoff, softon, upgrade, worklevel, ...):
 *
 *  - powerControl: `ascset|0,softoff` / `ascset|0,softon` — pause/resume hashing,
 *    verified live (reversible, no argument).
 *  - reboot: `ascset|0,reboot,0` — a wrong keyword is a harmless "Unknown reboot cmd"
 *    no-op on this firmware, so this is safe to attempt behind confirmation.
 *
 * fan-spd, worklevel, frequency, hashpower are intentionally NOT exposed: their
 * argument ranges are unverified and a wrong value could change power draw or thermal
 * behavior. They stay unsupported with that reason until verified per device.
 */
@Singleton
class CanaanAdapter @Inject constructor(
    private val api: CgMinerApi,
) : MinerControlAdapter {

    override val adapterType: String = TYPE
    override val displayName: String = "Canaan Avalon"
    override val defaultPort: Int = CgMinerApi.DEFAULT_PORT

    override suspend fun probe(host: MinerHost): ProbeResult {
        val port = apiPort(host)
        val result = api.query(host.host, port, "version")
        val body = result.body
            ?: return ProbeResult.Unreachable(result.error ?: "No response")
        val version = CanaanParser.parseVersion(body)
        if (!CanaanParser.isAvalon(version)) return ProbeResult.NotThisDevice
        // estats enriches identity with the hardware DNA serial when available.
        val mm = CanaanParser.mmFieldsOf(api.query(host.host, port, "estats").body)
        return ProbeResult.Supported(
            adapterType = TYPE,
            identity = CanaanParser.identityOf(version!!, mm),
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
        if (!CanaanParser.isAvalon(CanaanParser.parseVersion(versionBody))) {
            return TelemetryResult.ParseError("Host is not an Avalon device", versionBody)
        }
        val summary = api.query(host.host, port, "summary").body
        val estats = api.query(host.host, port, "estats").body
        val pools = api.query(host.host, port, "pools").body
        val coin = api.query(host.host, port, "coin").body
        if (summary == null && estats == null) {
            return TelemetryResult.Offline("Device answered version but not summary/estats")
        }
        val telemetry = CanaanParser.parseTelemetry(summary, estats, pools, coin)
        val raw = buildString {
            append("{\"version\":").append(versionBody)
            summary?.let { append(",\"summary\":").append(it) }
            estats?.let { append(",\"estats\":").append(it) }
            pools?.let { append(",\"pools\":").append(it) }
            coin?.let { append(",\"coin\":").append(it) }
            append("}")
        }
        return TelemetryResult.Success(telemetry, raw)
    }

    override fun getCapabilities(identity: MinerIdentity?): MinerCapabilities =
        MinerCapabilities(
            supported = setOf(Capability.TELEMETRY, Capability.POWER_CONTROL, Capability.REBOOT),
            unsupportedReasons = mapOf(
                Capability.SET_FAN to "Avalon fan-speed argument range is not yet verified on this firmware.",
                Capability.SET_OPERATING_MODE to "Work-mode (worklevel) value range is not yet verified on this firmware.",
                Capability.APPLY_APPROVED_TUNE to "Avalon frequency/voltage tuning is not exposed until verified per device.",
                Capability.SET_POOLS to "Pool changes require the authenticated web interface, not yet verified.",
                Capability.LOGS to "Avalon does not expose a log stream over the CGMiner API.",
            ),
        )

    // ------------------------------------------------------------------ controls ----

    override suspend fun getTuneOptions(host: MinerHost): TuneOptions? = null

    override suspend fun reboot(host: MinerHost): ActionResult =
        ascset(host, "reboot,0") { msg ->
            // Firmware validates the keyword; an unknown one is a harmless no-op.
            if (msg.contains("Unknow", ignoreCase = true))
                ActionResult.Failure("Reboot keyword not accepted by this firmware: $msg")
            else ActionResult.Success
        }

    override suspend fun powerControl(host: MinerHost, action: PowerAction): ActionResult {
        val cmd = if (action == PowerAction.PAUSE) "softoff" else "softon"
        return ascset(host, cmd) { ActionResult.Success }
    }

    override suspend fun setPrimaryPool(host: MinerHost, url: String, port: Int, worker: String): ActionResult =
        ActionResult.Unsupported("Pool changes require the authenticated Avalon web interface, not yet verified.")

    override suspend fun setFan(host: MinerHost, config: FanControl): ActionResult =
        ActionResult.Unsupported("Avalon fan-speed argument range is not yet verified on this firmware.")

    override suspend fun applyTune(host: MinerHost, frequencyMhz: Int, coreVoltageMv: Int): ActionResult =
        ActionResult.Unsupported("Avalon tuning is not exposed until frequency/voltage ranges are verified.")

    /** Send `ascset|0,<option>` and map the firmware's Msg through [onOk]. */
    private suspend fun ascset(
        host: MinerHost,
        option: String,
        onOk: (String) -> ActionResult,
    ): ActionResult {
        val result = api.query(host.host, apiPort(host), "ascset", "0,$option")
        val body = result.body ?: return ActionResult.Failure(result.error ?: "No response")
        val msg = Regex("\"Msg\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1) ?: body
        val statusChar = Regex("\"STATUS\"\\s*:\\s*\"([A-Z])\"").find(body)?.groupValues?.get(1)
        return if (statusChar == "E") ActionResult.Failure(msg) else onOk(msg)
    }

    /** The CGMiner API lives on 4028; ignore HTTP-ish ports handed in by generic flows. */
    private fun apiPort(host: MinerHost): Int =
        if (host.port == 0 || host.port == 80) CgMinerApi.DEFAULT_PORT else host.port

    companion object {
        const val TYPE = "canaan-cgminer"
    }
}
