package hi3.hashkit.domain.tune

/**
 * Pure analysis over persisted auto-tune sweep points for one miner. Builds the efficiency- and
 * hashrate-vs-frequency picture and recommends an optimal, thermally-safe operating point for a
 * chosen goal. No I/O — unit-testable against synthetic points.
 */
object TuneOptimizer {

    data class Point(
        val frequencyMhz: Int,
        val voltageMv: Int,
        val hashrateGhs: Double?,
        val powerW: Double?,
        val efficiencyJTh: Double?,
        val chipTempC: Double?,
        val overTemp: Boolean,
    )

    data class Summary(
        val sampleCount: Int,
        /** Most efficient thermally-safe point (lowest J/TH). */
        val bestEfficiency: Point?,
        /** Highest-hashrate thermally-safe point. */
        val bestHashrate: Point?,
        /**
         * For each swept frequency, the best (lowest-J/TH, safe) point measured there — the
         * efficiency curve, ascending by frequency.
         */
        val efficiencyCurve: List<Point>,
    )

    private fun Point.isSafeValid() =
        !overTemp && (hashrateGhs ?: 0.0) > 0.0 && efficiencyJTh != null && efficiencyJTh > 0.0

    fun summarize(points: List<Point>): Summary {
        val safe = points.filter { it.isSafeValid() }
        val curve = safe
            .groupBy { it.frequencyMhz }
            .mapNotNull { (_, pts) -> pts.minByOrNull { it.efficiencyJTh!! } }
            .sortedBy { it.frequencyMhz }
        return Summary(
            sampleCount = points.size,
            bestEfficiency = safe.minByOrNull { it.efficiencyJTh!! },
            bestHashrate = safe.maxByOrNull { it.hashrateGhs!! },
            efficiencyCurve = curve,
        )
    }

    /** Highest-hashrate safe point whose power stays at or below [maxPowerW]. */
    fun bestHashrateUnderPower(points: List<Point>, maxPowerW: Double): Point? =
        points.filter { it.isSafeValid() && (it.powerW ?: Double.MAX_VALUE) <= maxPowerW }
            .maxByOrNull { it.hashrateGhs!! }
}
