package hi3.hashkit.domain.rules

import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RuleEngineTest {

    private fun telemetry(
        chip: Double? = 60.0,
        hr: Double? = 1000.0,
        acc: Long? = null,
        rej: Long? = null,
    ) = MinerTelemetry(
        timestamp = Instant.now(),
        status = MinerStatus.ONLINE,
        hashrateGhs = if (hr == null) Sourced.unavailable() else Sourced.reported(hr),
        chipTempC = if (chip == null) Sourced.unavailable() else Sourced.measured(chip),
        sharesAccepted = acc,
        sharesRejected = rej,
    )

    @Test fun chipTempAboveMatchesAtOrOverThreshold() {
        val t = telemetry(chip = 78.0)
        assertTrue(RuleEngine.matches(RuleEngine.ConditionType.CHIP_TEMP_ABOVE, 75.0, t, MinerStatus.ONLINE, null))
        assertFalse(RuleEngine.matches(RuleEngine.ConditionType.CHIP_TEMP_ABOVE, 80.0, t, MinerStatus.ONLINE, null))
    }

    @Test fun offlineConditionUsesStatus() {
        val t = telemetry()
        assertTrue(RuleEngine.matches(RuleEngine.ConditionType.OFFLINE, null, t, MinerStatus.OFFLINE, null))
        assertFalse(RuleEngine.matches(RuleEngine.ConditionType.OFFLINE, null, t, MinerStatus.ONLINE, null))
    }

    @Test fun hashrateBelowPctNeedsExpectedAndOnline() {
        val t = telemetry(hr = 700.0) // 70% of 1000
        assertTrue(RuleEngine.matches(RuleEngine.ConditionType.HASHRATE_BELOW_PCT, 80.0, t, MinerStatus.ONLINE, 1000.0))
        assertFalse(RuleEngine.matches(RuleEngine.ConditionType.HASHRATE_BELOW_PCT, 60.0, t, MinerStatus.ONLINE, 1000.0))
        // No expected hashrate -> never matches.
        assertFalse(RuleEngine.matches(RuleEngine.ConditionType.HASHRATE_BELOW_PCT, 80.0, t, MinerStatus.ONLINE, null))
    }

    @Test fun rejectRateNeedsEnoughShares() {
        val low = telemetry(acc = 50, rej = 40) // under the 100-share floor
        assertFalse(RuleEngine.matches(RuleEngine.ConditionType.REJECT_RATE_ABOVE, 3.0, low, MinerStatus.ONLINE, null))
        val high = telemetry(acc = 900, rej = 100) // 10%
        assertTrue(RuleEngine.matches(RuleEngine.ConditionType.REJECT_RATE_ABOVE, 3.0, high, MinerStatus.ONLINE, null))
    }

    @Test fun missingDataNeverMatches() {
        val t = telemetry(chip = null)
        assertFalse(RuleEngine.matches(RuleEngine.ConditionType.CHIP_TEMP_ABOVE, 75.0, t, MinerStatus.ONLINE, null))
    }

    @Test fun sustainZeroMinutesFiresOnFirstMatch() {
        val d = RuleEngine.sustain(matchesNow = true, sinceEpochMs = null, nowEpochMs = 1_000L, sustainedForMinutes = 0)
        assertTrue(d.fire)
        assertTrue(d.sinceEpochMs == 1_000L)
    }

    @Test fun sustainWaitsForFullWindow() {
        val start = 1_000L
        // First observation: pending, not firing.
        val first = RuleEngine.sustain(true, null, start, sustainedForMinutes = 10)
        assertFalse(first.fire)
        // 9 minutes in: still pending.
        val pending = RuleEngine.sustain(true, first.sinceEpochMs, start + 9 * 60_000L, 10)
        assertFalse(pending.fire)
        assertTrue(pending.sinceEpochMs == start)
        // 10 minutes in: fires.
        val fired = RuleEngine.sustain(true, pending.sinceEpochMs, start + 10 * 60_000L, 10)
        assertTrue(fired.fire)
    }

    @Test fun sustainResetsWhenConditionClears() {
        val start = 1_000L
        val first = RuleEngine.sustain(true, null, start, 10)
        // Condition clears mid-window: state resets.
        val cleared = RuleEngine.sustain(false, first.sinceEpochMs, start + 5 * 60_000L, 10)
        assertFalse(cleared.fire)
        assertTrue(cleared.sinceEpochMs == null)
        // Matching again must re-earn the full window from the new start.
        val again = RuleEngine.sustain(true, cleared.sinceEpochMs, start + 6 * 60_000L, 10)
        assertFalse(again.fire)
        assertTrue(again.sinceEpochMs == start + 6 * 60_000L)
    }
}
