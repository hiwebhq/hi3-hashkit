package hi3.hashkit.domain.solo

import kotlin.math.expm1
import kotlin.math.ln

/**
 * Solo-mining probability. Mathematically: block finding is a Poisson process where the
 * expected number of blocks in time T at hashrate H against difficulty D is
 * lambda = H * T / (D * 2^32). P(at least one block) = 1 - e^(-lambda).
 *
 * These are statistical expectations, not predictions — the UI must always say so.
 */
object SoloMiningMath {

    private const val TWO_32 = 4294967296.0 // 2^32 hashes per unit difficulty

    /** Expected blocks over [seconds] at [hashrateGhs] against [networkDifficulty]. */
    fun expectedBlocks(hashrateGhs: Double, networkDifficulty: Double, seconds: Double): Double {
        if (hashrateGhs <= 0 || networkDifficulty <= 0 || seconds <= 0) return 0.0
        return hashrateGhs * 1e9 * seconds / (networkDifficulty * TWO_32)
    }

    /** Probability of finding at least one block over [seconds]. */
    fun probabilityAtLeastOneBlock(hashrateGhs: Double, networkDifficulty: Double, seconds: Double): Double {
        val lambda = expectedBlocks(hashrateGhs, networkDifficulty, seconds)
        // -expm1(-x) = 1 - e^(-x), numerically stable for tiny lambda.
        return -expm1(-lambda)
    }

    /** Expected time (seconds) to find one block — the statistical mean, not a promise. */
    fun expectedSecondsPerBlock(hashrateGhs: Double, networkDifficulty: Double): Double? {
        if (hashrateGhs <= 0 || networkDifficulty <= 0) return null
        return networkDifficulty * TWO_32 / (hashrateGhs * 1e9)
    }

    /** Time (seconds) at which the probability of at least one block reaches [p]. */
    fun secondsToReachProbability(hashrateGhs: Double, networkDifficulty: Double, p: Double): Double? {
        if (p <= 0 || p >= 1) return null
        val perSecond = expectedBlocks(hashrateGhs, networkDifficulty, 1.0)
        if (perSecond <= 0) return null
        return -ln(1 - p) / perSecond
    }

    fun formatProbability(p: Double): String = when {
        p <= 0.0 -> "0%"
        p < 1e-9 -> "< 0.0000001%"
        p < 0.000001 -> String.format(java.util.Locale.US, "%.7f%%", p * 100)
        p < 0.0001 -> String.format(java.util.Locale.US, "%.5f%%", p * 100)
        p < 0.01 -> String.format(java.util.Locale.US, "%.3f%%", p * 100)
        p > 0.999 -> "> 99.9%"
        else -> String.format(java.util.Locale.US, "%.1f%%", p * 100)
    }

    fun formatExpectedTime(seconds: Double?): String {
        if (seconds == null || seconds <= 0) return "—"
        val years = seconds / (365.25 * 86400)
        return when {
            years >= 1_000_000 -> String.format(java.util.Locale.US, "~%.1fM years", years / 1e6)
            years >= 1_000 -> String.format(java.util.Locale.US, "~%.0fk years", years / 1e3)
            years >= 1 -> String.format(java.util.Locale.US, "~%.1f years", years)
            seconds >= 86400 -> String.format(java.util.Locale.US, "~%.0f days", seconds / 86400)
            else -> String.format(java.util.Locale.US, "~%.0f hours", seconds / 3600)
        }
    }
}
