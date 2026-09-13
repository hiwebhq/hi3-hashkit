package hi3.hashkit.domain.acoustic

import androidx.annotation.StringRes
import hi3.hashkit.R

/**
 * Best-effort, INDICATIVE analysis of a miner-fan recording. It is not a calibrated diagnostic:
 * phone mics, room reflections and background noise all colour the result. It looks for the
 * gross acoustic signatures of fan trouble:
 *
 *  - a strong, low tonal peak → possible imbalance / debris on the blades,
 *  - raised broadband high-frequency energy with little tonality → possible bearing wear / whine.
 *
 * All maths is pure so it can be unit-tested against synthetic signals.
 */
object AcousticAnalyzer {

    /** One finding, resolved to text at render time (args are positional format args). */
    data class Finding(@StringRes val messageRes: Int, val args: List<Any> = emptyList())

    data class Result(
        val dominantHz: Double,
        val rpmEstimate: Int,
        /** Peak magnitude ÷ mean magnitude — how tonal (peaky) the sound is. */
        val tonalRatio: Double,
        /** Fraction of energy above [HIGH_FREQ_CUTOFF_HZ]. */
        val highFreqRatio: Double,
        val findings: List<Finding>,
        val healthy: Boolean,
    )

    private const val MIN_FAN_HZ = 20.0
    private const val MAX_FAN_HZ = 1000.0
    private const val HIGH_FREQ_CUTOFF_HZ = 2000.0
    private const val HIGH_FREQ_RATIO_WARN = 0.35
    private const val LOW_TONAL_WARN = 4.0
    private const val IMBALANCE_TONAL_WARN = 12.0
    private const val IMBALANCE_MAX_HZ = 60.0

    fun analyze(samples: FloatArray, sampleRate: Int): Result? {
        val spectrum = Fft.magnitudeSpectrum(samples)
        if (spectrum.size < 8 || sampleRate <= 0) return null
        val n = spectrum.size * 2
        val hzPerBin = sampleRate.toDouble() / n

        // Dominant tone within the plausible fan band (skip DC/rumble below MIN_FAN_HZ).
        val loBin = (MIN_FAN_HZ / hzPerBin).toInt().coerceAtLeast(1)
        val hiBin = (MAX_FAN_HZ / hzPerBin).toInt().coerceAtMost(spectrum.size - 1)
        var peakBin = loBin
        var peakMag = 0f
        for (b in loBin..hiBin) {
            if (spectrum[b] > peakMag) { peakMag = spectrum[b]; peakBin = b }
        }
        val dominantHz = peakBin * hzPerBin

        val meanMag = spectrum.drop(1).average().takeIf { it > 0 } ?: return null
        val tonalRatio = peakMag / meanMag

        val cutoffBin = (HIGH_FREQ_CUTOFF_HZ / hzPerBin).toInt().coerceIn(1, spectrum.size - 1)
        var totalEnergy = 0.0
        var highEnergy = 0.0
        for (b in 1 until spectrum.size) {
            val e = spectrum[b].toDouble() * spectrum[b]
            totalEnergy += e
            if (b >= cutoffBin) highEnergy += e
        }
        val highFreqRatio = if (totalEnergy > 0) highEnergy / totalEnergy else 0.0

        val findings = mutableListOf<Finding>()
        if (highFreqRatio > HIGH_FREQ_RATIO_WARN) {
            findings += Finding(R.string.acou_bearing)
        }
        if (dominantHz <= IMBALANCE_MAX_HZ && tonalRatio > IMBALANCE_TONAL_WARN) {
            findings += Finding(R.string.acou_imbalance, listOf(dominantHz.toInt()))
        }
        if (tonalRatio < LOW_TONAL_WARN && highFreqRatio <= HIGH_FREQ_RATIO_WARN) {
            findings += Finding(R.string.acou_broadband)
        }

        val healthy = findings.isEmpty()
        if (healthy) findings += Finding(R.string.acou_no_fault)

        return Result(
            dominantHz = dominantHz,
            rpmEstimate = (dominantHz * 60).toInt(),
            tonalRatio = tonalRatio,
            highFreqRatio = highFreqRatio,
            findings = findings,
            healthy = healthy,
        )
    }
}
