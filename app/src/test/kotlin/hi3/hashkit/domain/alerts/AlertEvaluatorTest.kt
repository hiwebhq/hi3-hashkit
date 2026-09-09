package hi3.hashkit.domain.alerts

import hi3.hashkit.domain.model.FanReading
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlertEvaluatorTest {

    private val thresholds = AlertThresholds()

    private fun telemetry(
        status: MinerStatus = MinerStatus.ONLINE,
        hashrate: Double? = 1000.0,
        expected: Double? = 1000.0,
        chipTemp: Double? = 55.0,
        uptime: Long? = 86_400,
        pool: String? = "pool.local",
        bestDiff: Double? = 1e6,
        fans: List<FanReading> = listOf(FanReading(0, rpm = 4000, percent = 70)),
    ) = MinerTelemetry(
        timestamp = Instant.now(),
        status = status,
        hashrateGhs = Sourced.reported(hashrate),
        expectedHashrateGhs = Sourced.reported(expected),
        chipTempC = Sourced.measured(chipTemp),
        fans = fans,
        uptimeSeconds = uptime,
        poolUrl = pool,
        bestDifficulty = bestDiff,
    )

    private fun eval(t: MinerTelemetry, prev: AlertEvaluator.PreviousState) =
        AlertEvaluator.evaluate(1, "TestMiner", t, null, thresholds, prev)

    @Test
    fun `single failed poll does not raise offline`() {
        val signals = eval(telemetry(status = MinerStatus.OFFLINE), AlertEvaluator.PreviousState())
        assertTrue(signals.none { it.type == AlertType.MINER_OFFLINE && it.active })
    }

    @Test
    fun `second consecutive failure raises offline once and recovery clears it`() {
        var state = AlertEvaluator.PreviousState()
        val offline = telemetry(status = MinerStatus.OFFLINE)

        var signals = eval(offline, state)
        state = AlertEvaluator.nextState(state, offline, signals)
        signals = eval(offline, state)
        assertTrue(signals.any { it.type == AlertType.MINER_OFFLINE && it.active })
        state = AlertEvaluator.nextState(state, offline, signals)

        // Third failure: already active, no duplicate signal (dedup by state).
        signals = eval(offline, state)
        assertTrue(signals.none { it.type == AlertType.MINER_OFFLINE })
        state = AlertEvaluator.nextState(state, offline, signals)

        // Recovery emits an inactive signal exactly once.
        val online = telemetry()
        signals = eval(online, state)
        assertEquals(1, signals.count { it.type == AlertType.MINER_OFFLINE && !it.active })
        state = AlertEvaluator.nextState(state, online, signals)
        assertTrue(AlertType.MINER_OFFLINE !in state.activeTypes)
    }

    @Test
    fun `low hashrate raises and recovers`() {
        var state = AlertEvaluator.PreviousState()
        val low = telemetry(hashrate = 500.0)
        var signals = eval(low, state)
        assertTrue(signals.any { it.type == AlertType.HASHRATE_BELOW_THRESHOLD && it.active })
        state = AlertEvaluator.nextState(state, low, signals)

        signals = eval(telemetry(hashrate = 990.0), state)
        assertTrue(signals.any { it.type == AlertType.HASHRATE_BELOW_THRESHOLD && !it.active })
    }

    @Test
    fun `restart detected when uptime goes backwards`() {
        val state = AlertEvaluator.PreviousState(previousUptimeS = 90_000)
        val signals = eval(telemetry(uptime = 60), state)
        assertTrue(signals.any { it.type == AlertType.UNEXPECTED_RESTART })
    }

    @Test
    fun `pool change and new best difficulty are one-shot events`() {
        val state = AlertEvaluator.PreviousState(
            previousPoolUrl = "old.pool", previousBestDifficulty = 1e6,
        )
        val signals = eval(telemetry(pool = "new.pool", bestDiff = 5e6), state)
        assertTrue(signals.any { it.type == AlertType.POOL_CHANGED })
        assertTrue(signals.any { it.type == AlertType.NEW_BEST_DIFFICULTY })
    }

    @Test
    fun `offline sample does not raise telemetry-based alerts`() {
        val signals = eval(
            telemetry(status = MinerStatus.OFFLINE, hashrate = null, chipTemp = null),
            AlertEvaluator.PreviousState(previousUptimeS = 100, previousPoolUrl = "x"),
        )
        assertTrue(signals.none { it.type == AlertType.HASHRATE_BELOW_THRESHOLD })
        assertTrue(signals.none { it.type == AlertType.UNEXPECTED_RESTART })
        assertTrue(signals.none { it.type == AlertType.POOL_CHANGED })
    }

    @Test
    fun `stalled fan raises alert`() {
        val signals = eval(
            telemetry(fans = listOf(FanReading(0, rpm = 0, percent = 80))),
            AlertEvaluator.PreviousState(),
        )
        assertTrue(signals.any { it.type == AlertType.FAN_STOPPED && it.active })
    }

    @Test
    fun `fallback pool raises pool disconnected and clears on reconnect`() {
        var state = AlertEvaluator.PreviousState()
        val onFallback = telemetry().copy(usingFallbackPool = true)

        var signals = eval(onFallback, state)
        assertTrue(signals.any { it.type == AlertType.POOL_DISCONNECTED && it.active })
        state = AlertEvaluator.nextState(state, onFallback, signals)

        // No duplicate while still on fallback.
        signals = eval(onFallback, state)
        assertTrue(signals.none { it.type == AlertType.POOL_DISCONNECTED })
        state = AlertEvaluator.nextState(state, onFallback, signals)

        // Back on primary -> recovery signal.
        val primary = telemetry().copy(usingFallbackPool = false)
        signals = eval(primary, state)
        assertTrue(signals.any { it.type == AlertType.POOL_DISCONNECTED && !it.active })
    }
}
