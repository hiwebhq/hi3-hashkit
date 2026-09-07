package hi3.hashkit.core

import java.util.Locale

/** Formatting helpers. Canonical internal units: GH/s, W, °C, J/TH. */
object Units {

    /** Format a hashrate given in GH/s with an auto-scaled unit (H/s..EH/s). */
    fun formatHashrate(ghs: Double?): String {
        if (ghs == null) return "—"
        val hs = ghs * 1e9
        val units = listOf("H/s", "kH/s", "MH/s", "GH/s", "TH/s", "PH/s", "EH/s")
        var v = hs
        var i = 0
        while (v >= 1000.0 && i < units.lastIndex) {
            v /= 1000.0
            i++
        }
        return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.2f %s", v, units[i])
    }

    fun formatPower(watts: Double?): String =
        when {
            watts == null -> "—"
            watts >= 1000 -> String.format(Locale.US, "%.2f kW", watts / 1000.0)
            else -> String.format(Locale.US, "%.1f W", watts)
        }

    fun formatTemp(celsius: Double?, fahrenheit: Boolean = false): String =
        when {
            celsius == null -> "—"
            fahrenheit -> String.format(Locale.US, "%.0f°F", celsius * 9.0 / 5.0 + 32.0)
            else -> String.format(Locale.US, "%.0f°C", celsius)
        }

    fun formatEfficiency(jPerTh: Double?): String =
        if (jPerTh == null) "—" else String.format(Locale.US, "%.1f J/TH", jPerTh)

    /** Efficiency in J/TH from power (W) and hashrate (GH/s). */
    fun efficiencyJTh(powerW: Double?, hashrateGhs: Double?): Double? {
        if (powerW == null || hashrateGhs == null || hashrateGhs <= 0.0) return null
        return powerW / (hashrateGhs / 1000.0)
    }

    /** Compact difficulty formatting: 4.29M, 1.2G, ... */
    fun formatDifficulty(diff: Double?): String {
        if (diff == null) return "—"
        val units = listOf("", "k", "M", "G", "T", "P", "E")
        var v = diff
        var i = 0
        while (v >= 1000.0 && i < units.lastIndex) {
            v /= 1000.0
            i++
        }
        return String.format(Locale.US, if (v >= 100 || i == 0) "%.0f%s" else "%.2f%s", v, units[i])
    }

    fun formatUptime(seconds: Long?): String {
        if (seconds == null) return "—"
        val d = seconds / 86400
        val h = seconds % 86400 / 3600
        val m = seconds % 3600 / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }
}
