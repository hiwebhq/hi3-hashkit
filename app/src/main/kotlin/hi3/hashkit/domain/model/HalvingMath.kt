package hi3.hashkit.domain.model

/** Pure Bitcoin epoch math for the halving + difficulty-adjustment countdown. */
object HalvingMath {

    const val HALVING_INTERVAL = 210_000L
    const val AVG_BLOCK_MINUTES = 10L

    /** The block height of the next halving after [height]. */
    fun nextHalvingBlock(height: Long): Long =
        (height / HALVING_INTERVAL + 1) * HALVING_INTERVAL

    /** Blocks remaining until the next halving. */
    fun blocksToHalving(height: Long): Long = nextHalvingBlock(height) - height

    /** Which reward era [height] is in (0 = 50 BTC era). */
    fun halvingEra(height: Long): Int = (height / HALVING_INTERVAL).toInt()

    /** Block subsidy in BTC at [height] (ignoring the eventual zero after 33 halvings). */
    fun subsidyBtc(height: Long): Double = 50.0 / Math.pow(2.0, halvingEra(height).toDouble())

    /** Rough time to the next halving, in milliseconds (10-min blocks). */
    fun timeToHalvingMs(height: Long): Long = blocksToHalving(height) * AVG_BLOCK_MINUTES * 60_000L

    /** Compact human duration like "412d", "3d 4h", "5h 12m". */
    fun humanDuration(ms: Long): String {
        if (ms <= 0) return "now"
        val totalMinutes = ms / 60_000
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        return when {
            days >= 10 -> "${days}d"
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}
