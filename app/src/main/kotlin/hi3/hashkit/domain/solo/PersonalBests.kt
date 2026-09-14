package hi3.hashkit.domain.solo

import java.util.Locale

/** Pure rules for the personal-best (best share) record book. */
object PersonalBests {

    /** A record is strictly higher than anything seen before; equal readings are the same share. */
    fun isNewRecord(candidateDifficulty: Double?, knownBest: Double?): Boolean {
        val candidate = candidateDifficulty ?: return false
        if (candidate <= 0) return false
        return candidate > (knownBest ?: 0.0)
    }

    /** How far that share got toward a block, as a percentage of network difficulty. */
    fun percentOfBlock(shareDifficulty: Double?, networkDifficulty: Double?): Double? {
        if (shareDifficulty == null || networkDifficulty == null || networkDifficulty <= 0) return null
        return shareDifficulty / networkDifficulty * PERCENT
    }

    /** Enough significant digits that a home miner's tiny fraction still reads as a number. */
    fun formatPercent(percent: Double?): String = when {
        percent == null -> "—"
        percent >= PERCENT -> "100%"
        percent >= ONE_DECIMAL_FROM -> String.format(Locale.US, "%.1f%%", percent)
        percent >= THREE_DECIMALS_FROM -> String.format(Locale.US, "%.3f%%", percent)
        percent >= FIVE_DECIMALS_FROM -> String.format(Locale.US, "%.5f%%", percent)
        else -> String.format(Locale.US, "%.2e%%", percent)
    }

    private const val PERCENT = 100.0
    private const val ONE_DECIMAL_FROM = 1.0
    private const val THREE_DECIMALS_FROM = 0.01
    private const val FIVE_DECIMALS_FROM = 0.000_01
}
