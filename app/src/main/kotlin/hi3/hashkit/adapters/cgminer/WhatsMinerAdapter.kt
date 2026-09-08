package hi3.hashkit.adapters.cgminer

import hi3.hashkit.domain.adapter.MinerAdapter
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.domain.adapter.ProbeResult
import hi3.hashkit.domain.adapter.TelemetryResult
import hi3.hashkit.domain.model.Capability
import hi3.hashkit.domain.model.MinerCapabilities
import hi3.hashkit.domain.model.MinerIdentity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitoring adapter for WhatsMiner (MicroBT / BTMiner) miners. Their local API lives on
 * TCP 4028 but uses `{"cmd":"<x>"}` requests (not cgminer's `{"command":...}`); read
 * commands (`summary`, `pools`, `get_miner_info`) are open and return cgminer-style JSON.
 *
 * SCOPE: monitoring only. Hashrate, shares, uptime and pool come from the standard summary
 * fields; chip temperature, fans and (where reported) power come from WhatsMiner's
 * documented summary fields. All write/control commands require MicroBT's encrypted admin
 * token and are intentionally unsupported until verified against a real unit.
 *
 * NOTE: response shapes are per MicroBT's documented API; pending confirmation against a
 * physical WhatsMiner on the user's network (marked compat-gated in the device matrix).
 */
@Singleton
class WhatsMinerAdapter @Inject constructor(
    private val api: CgMinerApi,
) : MinerAdapter {

    override val adapterType: String = TYPE
    override val displayName: String = "WhatsMiner (MicroBT)"
    override val defaultPort: Int = CgMinerApi.DEFAULT_PORT

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun probe(host: MinerHost): ProbeResult {
        val port = apiPort(host)
        val result = api.queryCmd(host.host, port, "summary")
        val body = result.body ?: return ProbeResult.Unreachable(result.error ?: "No response")
        if (!looksLikeWhatsMiner(body)) return ProbeResult.NotThisDevice
        val info = api.queryCmd(host.host, port, "get_miner_info").body
        return ProbeResult.Supported(TYPE, identity(info), body)
    }

    override suspend fun getIdentity(host: MinerHost): MinerIdentity? =
        (probe(host) as? ProbeResult.Supported)?.identity

    override suspend fun getTelemetry(host: MinerHost): TelemetryResult {
        val port = apiPort(host)
        val summary = api.queryCmd(host.host, port, "summary").body
            ?: return TelemetryResult.Offline("No summary from WhatsMiner API")
        if (!looksLikeWhatsMiner(summary)) {
            return TelemetryResult.ParseError("Not a WhatsMiner response", summary)
        }
        val pools = api.queryCmd(host.host, port, "pools").body
        var telemetry = CgMinerCommon.parseStandardTelemetry(summary, pools)
        telemetry = CgMinerCommon.enrichWhatsMiner(telemetry, summary)
        val raw = buildString {
            append("{\"summary\":").append(summary)
            pools?.let { append(",\"pools\":").append(it) }
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
                    "WhatsMiner controls require MicroBT's encrypted admin-token API, which " +
                        "is not yet verified — monitoring only."
                },
        )

    /** WhatsMiner summary reports its firmware as "btminer …" in the STATUS description. */
    private fun looksLikeWhatsMiner(body: String): Boolean {
        val desc = CgMinerCommon.firstStatusDescription(body)?.lowercase()
        if (desc != null && ("btminer" in desc || "whatsminer" in desc)) return true
        // Fallback: some firmwares only reveal it via get_miner_info / raw markers.
        val lower = body.lowercase()
        return "btminer" in lower || "whatsminer" in lower
    }

    private fun identity(minerInfoBody: String?): MinerIdentity {
        val msg = minerInfoBody?.let {
            runCatching { json.parseToJsonElement(it).jsonObject["Msg"] as? JsonObject }.getOrNull()
        }
        fun s(k: String) = (msg?.get(k) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        return MinerIdentity(
            manufacturer = "MicroBT (WhatsMiner)",
            model = s("minertype") ?: s("type"),
            firmwareFamily = "WhatsMiner",
            firmwareVersion = s("fw_ver") ?: s("Firmware Version"),
            macAddress = s("mac"),
        )
    }

    private fun apiPort(host: MinerHost): Int =
        if (host.port == 0 || host.port == 80) CgMinerApi.DEFAULT_PORT else host.port

    companion object {
        const val TYPE = "whatsminer"
    }
}
