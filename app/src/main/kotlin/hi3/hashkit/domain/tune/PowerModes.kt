package hi3.hashkit.domain.tune

import hi3.hashkit.domain.adapter.TuneOptions

/** The three-position switch a home miner actually wants: quiet, stock, a bit more. */
enum class PowerMode { QUIET, NORMAL, BOOST }

/**
 * Maps a [PowerMode] onto the device's own firmware-approved option lists — never a value
 * the firmware didn't publish. Frequency moves; voltage stays at stock so nothing runs
 * outside what the manufacturer ships:
 *  - QUIET  = the lowest approved frequency (cooler chip, slower fan, less noise),
 *  - NORMAL = the stock frequency,
 *  - BOOST  = [BOOST_STEPS] approved steps above stock (a mild, conventional overclock),
 *    capped at the highest approved value.
 */
object PowerModes {

    data class Point(val frequencyMhz: Int, val coreVoltageMv: Int)

    const val BOOST_STEPS = 2

    /** Null when the option lists are empty (nothing safe to choose from). */
    fun resolve(options: TuneOptions, mode: PowerMode, currentVoltageMv: Int? = null): Point? {
        val freqs = options.frequencyOptionsMhz.distinct().sorted()
        val volts = options.voltageOptionsMv.distinct().sorted()
        if (freqs.isEmpty() || volts.isEmpty()) return null
        val stockFreq = options.defaultFrequencyMhz?.takeIf { it in freqs } ?: freqs[freqs.size / 2]
        val volt = options.defaultVoltageMv?.takeIf { it in volts }
            ?: currentVoltageMv?.takeIf { it in volts }
            ?: volts[volts.size / 2]
        val stockIndex = freqs.indexOf(stockFreq)
        val freq = when (mode) {
            PowerMode.QUIET -> freqs.first()
            PowerMode.NORMAL -> stockFreq
            PowerMode.BOOST -> freqs[(stockIndex + BOOST_STEPS).coerceAtMost(freqs.lastIndex)]
        }
        return Point(freq, volt)
    }

    /** Which mode the miner is currently in, or null when it's on a custom point. */
    fun modeOf(options: TuneOptions, currentFrequencyMhz: Int?, currentVoltageMv: Int?): PowerMode? {
        if (currentFrequencyMhz == null || currentVoltageMv == null) return null
        return PowerMode.entries.firstOrNull { mode ->
            resolve(options, mode, currentVoltageMv) == Point(currentFrequencyMhz, currentVoltageMv)
        }
    }
}
