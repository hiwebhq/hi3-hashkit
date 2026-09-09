package hi3.hashkit.integrations.metrics

import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerIdentity
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MetricsFormatterTest {

    private fun miner(id: Long, name: String, hr: Double, status: MinerStatus = MinerStatus.ONLINE) =
        Miner(
            id = id, stableKey = "k$id", adapterType = "espminer", name = name,
            host = "10.0.0.$id", port = 80, identity = MinerIdentity(), status = status,
            lastTelemetry = MinerTelemetry(
                timestamp = Instant.now(), status = status,
                hashrateGhs = Sourced.reported(hr), powerW = Sourced.reported(15.0),
            ),
        )

    @Test fun rendersFleetAndPerMinerGauges() {
        val out = MetricsFormatter.render(
            listOf(miner(1, "Bitaxe A", 1000.0), miner(2, "Bitaxe B", 500.0, MinerStatus.OFFLINE)),
        )
        assertTrue(out.contains("# TYPE hi3_fleet_hashrate_ghs gauge"))
        // Only the online miner contributes to the fleet hashrate.
        assertTrue(out.contains("hi3_fleet_hashrate_ghs 1000.000"))
        assertTrue(out.contains("hi3_fleet_miners_online 1"))
        assertTrue(out.contains("hi3_fleet_miners_total 2"))
        assertTrue(out.contains("hi3_miner_hashrate_ghs{id=\"1\",miner=\"Bitaxe A\"} 1000.000"))
        assertTrue(out.contains("hi3_miner_up{id=\"2\",miner=\"Bitaxe B\"} 0"))
    }

    @Test fun demoMinersAreExcluded() {
        val demo = miner(9, "Demo", 999.0).copy(isDemo = true)
        val out = MetricsFormatter.render(listOf(demo))
        assertFalse(out.contains("Demo"))
        assertTrue(out.contains("hi3_fleet_miners_total 0"))
    }

    @Test fun labelValuesAreEscaped() {
        val out = MetricsFormatter.render(listOf(miner(1, "A\"B", 100.0)))
        assertTrue(out.contains("miner=\"A\\\"B\""))
    }
}
