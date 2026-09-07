package hi3.hashkit.domain.health

import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import kotlin.math.roundToInt

/** A 0–100 score that is always accompanied by its reasons — never a black box. */
data class HealthScore(
    val score: Int,
    val reasons: List<Reason>,
) {
    /** One deduction (or informational note) with its cost in points. */
    data class Reason(val points: Int, val text: String)
}

/** Tunable thresholds; defaults are conservative. Stored in settings. */
data class HealthThresholds(
    val chipTempWarnC: Double = 65.0,
    val chipTempCritC: Double = 70.0,
    val vrTempWarnC: Double = 80.0,
    val vrTempCritC: Double = 90.0,
    val attainmentWarnPercent: Double = 90.0,
    val attainmentCritPercent: Double = 75.0,
    val rejectRateWarnPercent: Double = 2.0,
    val uptimeRestartWindowS: Long = 900,
)

object HealthScoreCalculator {

    /**
     * Explainable score. Every deduction is returned as a reason with its point cost.
     * [previousUptimeS] lets the calculator flag unexpected restarts (uptime went backwards).
     */
    fun calculate(
        telemetry: MinerTelemetry?,
        expectedHashrateGhs: Double?,
        thresholds: HealthThresholds = HealthThresholds(),
        previousUptimeS: Long? = null,
    ): HealthScore {
        if (telemetry == null || telemetry.status == MinerStatus.OFFLINE) {
            return HealthScore(0, listOf(HealthScore.Reason(100, "Miner is offline or has never reported telemetry.")))
        }

        val reasons = mutableListOf<HealthScore.Reason>()
        var score = 100.0

        fun deduct(points: Double, text: String) {
            score -= points
            reasons += HealthScore.Reason(points.roundToInt(), text)
        }

        // Hashrate attainment vs expected (device-reported expected, or user-configured).
        val expected = expectedHashrateGhs ?: telemetry.expectedHashrateGhs.value
        val actual = telemetry.hashrateGhs.value
        if (actual == null) {
            deduct(30.0, "No hashrate is being reported.")
        } else if (expected != null && expected > 0) {
            val attainment = actual / expected * 100.0
            when {
                attainment < thresholds.attainmentCritPercent ->
                    deduct(30.0, "Hashrate is ${(100 - attainment).roundToInt()}% below expected.")
                attainment < thresholds.attainmentWarnPercent ->
                    deduct(12.0, "Hashrate is ${(100 - attainment).roundToInt()}% below expected.")
                else -> Unit
            }
        }

        // Chip temperature.
        telemetry.chipTempC.value?.let { t ->
            when {
                t >= thresholds.chipTempCritC ->
                    deduct(25.0, "Chip temperature ${t.roundToInt()}°C exceeds the critical limit (${thresholds.chipTempCritC.roundToInt()}°C).")
                t >= thresholds.chipTempWarnC ->
                    deduct(10.0, "Chip temperature ${t.roundToInt()}°C is above the warning level (${thresholds.chipTempWarnC.roundToInt()}°C).")
                else -> Unit
            }
        }

        // VR temperature (0 readings on some firmware mean "no sensor" — skip those).
        telemetry.vrTempC.value?.takeIf { it > 1.0 }?.let { t ->
            when {
                t >= thresholds.vrTempCritC ->
                    deduct(25.0, "Voltage-regulator temperature ${t.roundToInt()}°C exceeds the critical limit (${thresholds.vrTempCritC.roundToInt()}°C).")
                t >= thresholds.vrTempWarnC ->
                    deduct(10.0, "Voltage-regulator temperature ${t.roundToInt()}°C is above the warning level (${thresholds.vrTempWarnC.roundToInt()}°C).")
                else -> Unit
            }
        }

        // Fans: a fan reporting zero RPM while a percent is commanded is likely stalled.
        telemetry.fans.forEach { fan ->
            if (fan.rpm == 0 && (fan.percent ?: 0) > 0) {
                deduct(20.0, "Fan ${fan.index + 1} is reporting zero RPM.")
            }
        }

        // Rejected shares.
        val accepted = telemetry.sharesAccepted
        val rejected = telemetry.sharesRejected
        if (accepted != null && rejected != null && accepted + rejected > 100) {
            val rejectRate = rejected.toDouble() / (accepted + rejected) * 100.0
            if (rejectRate > thresholds.rejectRateWarnPercent) {
                deduct(
                    10.0,
                    "Rejected-share rate ${"%.1f".format(rejectRate)}% exceeds ${"%.1f".format(thresholds.rejectRateWarnPercent)}%.",
                )
            }
        }

        // Unexpected restart: uptime moved backwards since the previous sample.
        val uptime = telemetry.uptimeSeconds
        if (uptime != null && previousUptimeS != null && uptime < previousUptimeS) {
            deduct(10.0, "Miner restarted recently (uptime reset).")
        } else if (uptime != null && uptime < thresholds.uptimeRestartWindowS) {
            deduct(5.0, "Miner has been up for less than ${thresholds.uptimeRestartWindowS / 60} minutes.")
        }

        // Missing telemetry that limits diagnosis.
        if (telemetry.powerW.value == null) {
            deduct(3.0, "Power is not reported by this firmware; efficiency cannot be verified.")
        }

        if (telemetry.status == MinerStatus.UNKNOWN) {
            deduct(15.0, "Latest reading is stale; current state is unknown.")
        }

        if (reasons.isEmpty()) {
            reasons += HealthScore.Reason(0, "All monitored signals are within normal ranges.")
        }

        return HealthScore(score.coerceIn(0.0, 100.0).roundToInt(), reasons)
    }
}
