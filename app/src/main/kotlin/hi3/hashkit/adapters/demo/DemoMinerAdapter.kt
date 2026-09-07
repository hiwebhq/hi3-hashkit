package hi3.hashkit.adapters.demo

import hi3.hashkit.core.Units
import hi3.hashkit.domain.adapter.AdapterRegistry
import hi3.hashkit.domain.adapter.MinerAdapter
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.domain.adapter.ProbeResult
import hi3.hashkit.domain.adapter.TelemetryResult
import hi3.hashkit.domain.model.Capability
import hi3.hashkit.domain.model.FanReading
import hi3.hashkit.domain.model.MinerCapabilities
import hi3.hashkit.domain.model.MinerIdentity
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthetic miner for DEMO MODE ONLY. Never probes real hosts, never mixes with real
 * miners: demo miners are flagged isDemo in the database and rendered with a demo badge.
 */
@Singleton
class DemoMinerAdapter @Inject constructor() : MinerAdapter {

    override val adapterType: String = AdapterRegistry.DEMO_ADAPTER_TYPE
    override val displayName: String = "Demo miner (synthetic)"

    override suspend fun probe(host: MinerHost): ProbeResult = ProbeResult.NotThisDevice

    override suspend fun getIdentity(host: MinerHost): MinerIdentity = identityFor(host.host)

    override suspend fun getTelemetry(host: MinerHost): TelemetryResult {
        val seed = host.host.hashCode()
        val rnd = Random(seed + (System.currentTimeMillis() / 10_000))
        val t = System.currentTimeMillis() / 1000.0
        val baseGhs = 1100.0 + (seed % 7) * 55.0
        val hashrate = baseGhs * (0.94 + 0.06 * sin(t / 60.0 + seed)) + rnd.nextDouble(-15.0, 15.0)
        val power = 15.5 + rnd.nextDouble(-0.4, 0.4)
        return TelemetryResult.Success(
            telemetry = MinerTelemetry(
                timestamp = Instant.now(),
                status = MinerStatus.ONLINE,
                hashrateGhs = Sourced.reported(hashrate),
                expectedHashrateGhs = Sourced.reported(baseGhs),
                powerW = Sourced.measured(power),
                efficiencyJTh = Sourced.calculated(Units.efficiencyJTh(power, hashrate)),
                chipTempC = Sourced.measured(58.0 + rnd.nextDouble(-2.0, 3.0)),
                vrTempC = Sourced.measured(64.0 + rnd.nextDouble(-2.0, 3.0)),
                fans = listOf(FanReading(0, rpm = 4200 + rnd.nextInt(-150, 150), percent = 65)),
                frequencyMhz = Sourced.reported(525.0),
                coreVoltageMv = Sourced.reported(1150.0),
                asicCount = 1,
                sharesAccepted = (t / 12).toLong(),
                sharesRejected = (t / 1800).toLong(),
                bestDifficulty = 4.29e6,
                uptimeSeconds = (t % 864000).toLong(),
                poolUrl = "demo.pool.invalid",
                poolPort = 3333,
                workerName = "demo.worker",
            ),
            rawResponse = "{\"demo\":true}",
        )
    }

    override fun getCapabilities(identity: MinerIdentity?): MinerCapabilities =
        MinerCapabilities.monitoringOnly("Demo miners are synthetic — controls are meaningless.")
            .let { it.copy(supported = it.supported + Capability.TELEMETRY) }

    private fun identityFor(host: String): MinerIdentity = MinerIdentity(
        macAddress = null,
        hostname = "demo-$host",
        manufacturer = "Hi3 Demo",
        model = "Demo Bitaxe (synthetic)",
        asicModel = "DEMO",
        firmwareFamily = "demo",
        firmwareVersion = "0.0",
    )
}
