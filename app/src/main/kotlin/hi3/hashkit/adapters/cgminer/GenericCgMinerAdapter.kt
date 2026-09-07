package hi3.hashkit.adapters.cgminer

import hi3.hashkit.domain.adapter.MinerAdapter
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.domain.adapter.ProbeResult
import hi3.hashkit.domain.adapter.TelemetryResult
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
) : MinerAdapter {

    override val adapterType: String = TYPE
    override val displayName: String = "Antminer-class (cgminer)"
    override val defaultPort: Int = CgMinerApi.DEFAULT_PORT

    override suspend fun probe(host: MinerHost): ProbeResult {
        val result = api.query(host.host, apiPort(host), "version")
        val body = result.body ?: return ProbeResult.Unreachable(result.error ?: "No response")
        val family = CgMinerCommon.family(body)
        // Decline anything a specific adapter owns, or that isn't recognizably cgminer.
        if (family == null || family == CgMinerCommon.Family.AVALON ||
            family == CgMinerCommon.Family.BOSER || family == CgMinerCommon.Family.UNKNOWN
        ) {
            return ProbeResult.NotThisDevice
        }
        return ProbeResult.Supported(TYPE, identity(body, family), body)
    }

    override suspend fun getIdentity(host: MinerHost): MinerIdentity? =
        (probe(host) as? ProbeResult.Supported)?.identity

    override suspend fun getTelemetry(host: MinerHost): TelemetryResult {
        val port = apiPort(host)
        val version = api.query(host.host, port, "version")
        val versionBody = version.body ?: return TelemetryResult.Offline(version.error ?: "No response")
        val family = CgMinerCommon.family(versionBody)
        if (family == null || family == CgMinerCommon.Family.AVALON || family == CgMinerCommon.Family.BOSER) {
            return TelemetryResult.ParseError("Not an Antminer-class cgminer device", versionBody)
        }
        val summary = api.query(host.host, port, "summary").body
        val pools = api.query(host.host, port, "pools").body
        if (summary == null) return TelemetryResult.Offline("No summary from cgminer API")
        var telemetry = CgMinerCommon.parseStandardTelemetry(summary, pools)
        // Stock Bitmain: enrich with verified stats fields (temps/fans/freq/expected).
        var statsBody: String? = null
        if (family == CgMinerCommon.Family.ANTMINER_STOCK) {
            statsBody = api.query(host.host, port, "stats").body
            telemetry = CgMinerCommon.enrichWithAntminerStats(telemetry, statsBody)
        }
        val raw = buildString {
            append("{\"version\":").append(versionBody)
            append(",\"summary\":").append(summary)
            pools?.let { append(",\"pools\":").append(it) }
            statsBody?.let { append(",\"stats\":").append(it) }
            append("}")
        }
        return TelemetryResult.Success(telemetry, raw)
    }

    override fun getCapabilities(identity: MinerIdentity?): MinerCapabilities =
        MinerCapabilities(
            supported = setOf(Capability.TELEMETRY),
            unsupportedReasons = Capability.entries
                .filter { it != Capability.TELEMETRY }
                .associateWith {
                    "Temperature, fan, power and controls for this firmware are not yet " +
                        "verified — monitoring shows hashrate, shares, uptime and pool only."
                },
        )

    private fun identity(versionBody: String, family: CgMinerCommon.Family): MinerIdentity {
        val v = CgMinerCommon.firstRecord(versionBody, "VERSION")
        return MinerIdentity(
            manufacturer = CgMinerCommon.familyLabel(family),
            model = v?.let { (it["Type"] as? kotlinx.serialization.json.JsonPrimitive)?.content },
            firmwareFamily = CgMinerCommon.familyLabel(family),
            firmwareVersion = v?.let {
                ((it["CGMiner"] ?: it["LUXminer"]) as? kotlinx.serialization.json.JsonPrimitive)?.content
            },
        )
    }

    private fun apiPort(host: MinerHost): Int =
        if (host.port == 0 || host.port == 80) CgMinerApi.DEFAULT_PORT else host.port

    companion object {
        const val TYPE = "cgminer-generic"
    }
}
