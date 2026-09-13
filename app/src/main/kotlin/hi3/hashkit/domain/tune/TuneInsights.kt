package hi3.hashkit.domain.tune

import hi3.hashkit.data.db.SettingsPeriodStat

/**
 * Turns observed operating-point history (telemetry grouped by frequency/voltage) into
 * decisions: which settings actually ran best, and how a miner compares to its
 * same-model peers. Pure and unit-testable.
 */
object TuneInsights {

    data class Ranked(
        /** All qualifying operating points, most-observed first. */
        val periods: List<SettingsPeriodStat>,
        /** Lowest J/TH among points that stayed under the temp limit (when known). */
        val bestEfficiency: SettingsPeriodStat?,
        /** Highest average hashrate among points that stayed under the temp limit. */
        val bestHashrate: SettingsPeriodStat?,
    )

    /**
     * Rank observed operating points. Points whose max temp exceeded [tempLimitC]
     * (when provided) are excluded from "best" picks — a fast-but-cooking setting is
     * not a recommendation — but stay in [Ranked.periods] for display.
     */
    fun rank(periods: List<SettingsPeriodStat>, tempLimitC: Double?): Ranked {
        val safe = periods.filter { p ->
            tempLimitC == null || p.maxChipTempC == null || p.maxChipTempC < tempLimitC
        }
        return Ranked(
            periods = periods,
            bestEfficiency = safe.filter { it.avgEfficiencyJTh != null }
                .minByOrNull { it.avgEfficiencyJTh!! },
            bestHashrate = safe.filter { it.avgHashrateGhs != null }
                .maxByOrNull { it.avgHashrateGhs!! },
        )
    }

    /**
     * How this miner's hashrate compares to the median of its same-model peers, in
     * percent (negative = behind). Null when either side lacks data or peers are empty.
     */
    fun peerGapPercent(mine: Double?, peers: List<Double>): Double? {
        if (mine == null || mine <= 0 || peers.isEmpty()) return null
        val sorted = peers.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        if (median <= 0) return null
        return (mine - median) / median * PERCENT
    }

    private const val PERCENT = 100.0
}
