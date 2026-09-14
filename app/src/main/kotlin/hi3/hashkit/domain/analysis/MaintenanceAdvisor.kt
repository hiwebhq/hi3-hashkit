package hi3.hashkit.domain.analysis

/**
 * Dust nudge: a miner whose chip temperature has crept up over a month while drawing the
 * same power is almost always a clogged fan or heatsink, not a firmware problem. Compares
 * a recent week against a week roughly a month earlier, using the hourly rollups, and
 * only speaks when both windows have enough online hours and the power draw matches —
 * otherwise a tune change or summer weather would trigger it.
 *
 * Pure and clock-free (the caller passes `now`) so it is unit-testable.
 */
object MaintenanceAdvisor {

    /** One hour of rolled-up telemetry; mirrors the persisted hourly row's relevant fields. */
    data class HourPoint(
        val hourStartEpochMs: Long,
        val avgChipTempC: Double?,
        val avgPowerW: Double?,
        val onlineSamples: Int,
    )

    data class Finding(
        val tempRiseC: Double,
        val recentAvgC: Double,
        val baselineAvgC: Double,
        val avgPowerW: Double,
    ) {
        /** Persisted alert wording (English by convention, like every other alert). */
        val message: String
            get() = "chip temperature is up ${"%.1f".format(tempRiseC)}°C versus a month ago at the same " +
                "power draw (${"%.0f".format(baselineAvgC)}→${"%.0f".format(recentAvgC)}°C) — " +
                "time to clean the fan and heatsink."
    }

    private data class Window(val avgTempC: Double, val avgPowerW: Double, val hours: Int)

    /**
     * @param hours hourly points covering at least the last [BASELINE_END_DAYS]+[WINDOW_DAYS] days.
     * @return a finding when the recent week runs at least [riseThresholdC] hotter than the
     *   baseline week at comparable power, else null.
     */
    fun analyze(
        hours: List<HourPoint>,
        nowEpochMs: Long,
        riseThresholdC: Double = DEFAULT_RISE_C,
        minHoursPerWindow: Int = DEFAULT_MIN_HOURS,
        powerTolerance: Double = DEFAULT_POWER_TOLERANCE,
    ): Finding? {
        val recent = summarize(hours, nowEpochMs - WINDOW_DAYS * DAY_MS, nowEpochMs) ?: return null
        val baselineEnd = nowEpochMs - BASELINE_END_DAYS * DAY_MS
        val baseline = summarize(hours, baselineEnd - WINDOW_DAYS * DAY_MS, baselineEnd) ?: return null
        val enoughHours = recent.hours >= minHoursPerWindow && baseline.hours >= minHoursPerWindow
        val samePower = baseline.avgPowerW > 0 &&
            kotlin.math.abs(recent.avgPowerW - baseline.avgPowerW) / baseline.avgPowerW <= powerTolerance
        val rise = recent.avgTempC - baseline.avgTempC
        if (!enoughHours || !samePower || rise < riseThresholdC) return null
        return Finding(
            tempRiseC = rise,
            recentAvgC = recent.avgTempC,
            baselineAvgC = baseline.avgTempC,
            avgPowerW = recent.avgPowerW,
        )
    }

    private fun summarize(hours: List<HourPoint>, fromMs: Long, toMs: Long): Window? {
        val usable = hours.filter {
            it.hourStartEpochMs >= fromMs && it.hourStartEpochMs < toMs &&
                it.onlineSamples > 0 && it.avgChipTempC != null && it.avgPowerW != null
        }
        if (usable.isEmpty()) return null
        return Window(
            avgTempC = usable.map { it.avgChipTempC!! }.average(),
            avgPowerW = usable.map { it.avgPowerW!! }.average(),
            hours = usable.size,
        )
    }

    const val DAY_MS = 86_400_000L
    /** Each compared window is one week of hours. */
    const val WINDOW_DAYS = 7L
    /** The baseline week ends this many days before now. */
    const val BASELINE_END_DAYS = 28L
    /** How far back the caller must fetch hourly rows for a full comparison. */
    const val LOOKBACK_DAYS = BASELINE_END_DAYS + WINDOW_DAYS

    const val DEFAULT_RISE_C = 4.0
    const val DEFAULT_MIN_HOURS = 48
    const val DEFAULT_POWER_TOLERANCE = 0.10
}
