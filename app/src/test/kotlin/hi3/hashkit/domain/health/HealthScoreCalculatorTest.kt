package hi3.hashkit.domain.health

import hi3.hashkit.domain.model.FanReading
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HealthScoreCalculatorTest {

    private fun telemetry(
        hashrate: Double? = 1000.0,
        expected: Double? = 1000.0,
        chipTemp: Double? = 55.0,
        vrTemp: Double? = 60.0,
        fans: List<FanReading> = listOf(FanReading(0, rpm = 4000, percent = 70)),
        accepted: Long? = 10_000,
        rejected: Long? = 10,
        uptime: Long? = 86_400,
        power: Double? = 15.0,
        status: MinerStatus = MinerStatus.ONLINE,
    ) = MinerTelemetry(
        timestamp = Instant.now(),
        status = status,
        hashrateGhs = Sourced.reported(hashrate),
        expectedHashrateGhs = Sourced.reported(expected),
        powerW = Sourced.measured(power),
        chipTempC = Sourced.measured(chipTemp),
        vrTempC = Sourced.measured(vrTemp),
        fans = fans,
        sharesAccepted = accepted,
        sharesRejected = rejected,
        uptimeSeconds = uptime,
    )

    @Test
    fun `healthy miner scores 100 with a positive reason`() {
        val score = HealthScoreCalculator.calculate(telemetry(), null)
        assertEquals(100, score.score)
        assertTrue(score.reasons.single().text.contains("normal"))
    }

    @Test
    fun `offline miner scores 0`() {
        val score = HealthScoreCalculator.calculate(telemetry(status = MinerStatus.OFFLINE), null)
        assertEquals(0, score.score)
    }

    @Test
    fun `every deduction is explained`() {
        val score = HealthScoreCalculator.calculate(
            telemetry(hashrate = 700.0, expected = 1000.0, chipTemp = 72.0, vrTemp = 92.0),
            null,
        )
        assertTrue(score.score < 40)
        assertTrue(score.reasons.any { it.text.contains("below expected") })
        assertTrue(score.reasons.any { it.text.contains("Chip temperature") })
        assertTrue(score.reasons.any { it.text.contains("Voltage-regulator") })
        // The point costs shown must sum to what was deducted.
        assertEquals(100 - score.score, score.reasons.sumOf { it.points })
    }

    @Test
    fun `zero rpm fan with commanded speed is flagged`() {
        val score = HealthScoreCalculator.calculate(
            telemetry(fans = listOf(FanReading(0, rpm = 0, percent = 80))),
            null,
        )
        assertTrue(score.reasons.any { it.text.contains("zero RPM") })
    }

    @Test
    fun `vr temp of zero means no sensor and is not penalized`() {
        val score = HealthScoreCalculator.calculate(telemetry(vrTemp = 0.0), null)
        assertEquals(100, score.score)
    }

    @Test
    fun `uptime going backwards flags a restart`() {
        val score = HealthScoreCalculator.calculate(
            telemetry(uptime = 120), null, previousUptimeS = 86_400,
        )
        assertTrue(score.reasons.any { it.text.contains("restarted") })
    }

    @Test
    fun `user-configured expected hashrate overrides device-reported`() {
        val score = HealthScoreCalculator.calculate(
            telemetry(hashrate = 500.0, expected = 500.0),
            expectedHashrateGhs = 1000.0,
        )
        assertTrue(score.reasons.any { it.text.contains("below expected") })
    }
}
