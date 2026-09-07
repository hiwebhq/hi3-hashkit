package hi3.hashkit.adapters.canaan

import hi3.hashkit.domain.adapter.MinerAdapter
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.domain.adapter.ProbeResult
import hi3.hashkit.domain.adapter.TelemetryResult
import hi3.hashkit.domain.model.MinerCapabilities
import hi3.hashkit.domain.model.MinerIdentity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitoring adapter for Canaan Avalon home miners (Nano 3 verified live; Nano 3S and
 * Avalon Q identify through the same CGMiner API and are accepted when their `version`
 * response is demonstrably compatible).
 *
 * MONITORING ONLY: Canaan's control surface (work mode, fan, reboot) lives behind the
 * authenticated web CGI (`login.cgi` + session), which has not been captured and
 * verified yet. Per project policy no control endpoint is guessed — every control
 * capability is reported unsupported with this reason.
 */
@Singleton
class CanaanAdapter @Inject constructor(
    private val api: CanaanCgApi,
) : MinerAdapter {

    override val adapterType: String = TYPE
    override val displayName: String = "Canaan Avalon"
    override val defaultPort: Int = CanaanCgApi.DEFAULT_PORT

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
        MinerCapabilities.monitoringOnly(
            "Canaan controls use the authenticated web interface, which has not been " +
                "verified yet — monitoring only. Work-mode and fan controls will be added " +
                "once captured and tested against real firmware."
        )

    /** The CGMiner API lives on 4028; ignore HTTP-ish ports handed in by generic flows. */
    private fun apiPort(host: MinerHost): Int =
        if (host.port == 0 || host.port == 80) CanaanCgApi.DEFAULT_PORT else host.port

    companion object {
        const val TYPE = "canaan-cgminer"
    }
}
