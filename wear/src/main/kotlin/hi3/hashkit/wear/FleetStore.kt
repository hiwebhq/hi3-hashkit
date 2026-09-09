package hi3.hashkit.wear

import android.content.Context

/** The last fleet summary the watch received from the phone. */
data class FleetSummary(
    val totalHashrateGhs: Double,
    val online: Int,
    val offline: Int,
    val total: Int,
    val worstTempC: Double?,
    val updatedAtMs: Long,
) {
    val isEmpty: Boolean get() = updatedAtMs == 0L
}

/**
 * Caches the most recent summary in SharedPreferences so the tile can render it
 * synchronously (a tile request must return promptly) without touching the Data Layer.
 * The [FleetDataListenerService] writes here; the tile and activity read.
 */
object FleetStore {
    private const val PREFS = "wear_fleet"

    fun save(context: Context, s: FleetSummary) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(WearContract.KEY_TOTAL_HASHRATE_GHS, s.totalHashrateGhs.toFloat())
            .putInt(WearContract.KEY_ONLINE, s.online)
            .putInt(WearContract.KEY_OFFLINE, s.offline)
            .putInt(WearContract.KEY_TOTAL, s.total)
            .putFloat(WearContract.KEY_WORST_TEMP_C, (s.worstTempC ?: Double.NaN).toFloat())
            .putLong(WearContract.KEY_UPDATED_AT_MS, s.updatedAtMs)
            .apply()
    }

    fun load(context: Context): FleetSummary {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val worst = p.getFloat(WearContract.KEY_WORST_TEMP_C, Float.NaN)
        return FleetSummary(
            totalHashrateGhs = p.getFloat(WearContract.KEY_TOTAL_HASHRATE_GHS, 0f).toDouble(),
            online = p.getInt(WearContract.KEY_ONLINE, 0),
            offline = p.getInt(WearContract.KEY_OFFLINE, 0),
            total = p.getInt(WearContract.KEY_TOTAL, 0),
            worstTempC = worst.takeUnless { it.isNaN() }?.toDouble(),
            updatedAtMs = p.getLong(WearContract.KEY_UPDATED_AT_MS, 0L),
        )
    }
}

/** Minimal hashrate formatter (the watch module can't depend on the phone app's Units). */
object WearFormat {
    fun hashrate(ghs: Double): String {
        if (ghs <= 0.0) return "0 GH/s"
        val units = listOf("GH/s", "TH/s", "PH/s", "EH/s")
        var value = ghs
        var i = 0
        while (value >= 1000.0 && i < units.lastIndex) {
            value /= 1000.0
            i++
        }
        return if (value >= 100) "%.0f %s".format(value, units[i])
        else "%.2f %s".format(value, units[i])
    }
}
