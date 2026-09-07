package hi3.hashkit.adapters.braiins

import hi3.hashkit.adapters.cgminer.CgMinerApi
import hi3.hashkit.domain.adapter.MinerAdapter
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.domain.adapter.ProbeResult
import hi3.hashkit.domain.adapter.TelemetryResult
import hi3.hashkit.domain.model.MinerCapabilities
import hi3.hashkit.domain.model.MinerIdentity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitoring adapter for Braiins OS devices via the BOSer CGMiner-compatible TCP API
 * (verified live: Braiins Mini Miner BMM 100).
 *
 * MONITORING ONLY: Braiins OS control surfaces (its gRPC Public API and web UI) are
 * not yet captured and verified, so every control capability is reported unsupported
 * with that reason. Larger Braiins OS+ units (converted Antminers) speak the same
 * cgminer-style API family and will be accepted for monitoring when they identify as
 * BOSer, but are unverified until a real unit answers.
 */
@Singleton
class BraiinsAdapter @Inject constructor(
    private val api: CgMinerApi,
) : MinerAdapter {

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
        MinerCapabilities.monitoringOnly(
            "Braiins OS controls use its gRPC/web APIs, which have not been verified " +
                "yet — monitoring only."
        )

    private fun apiPort(host: MinerHost): Int =
        if (host.port == 0 || host.port == 80) CgMinerApi.DEFAULT_PORT else host.port

    companion object {
        const val TYPE = "braiins-boser"
    }
}
