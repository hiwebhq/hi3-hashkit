package hi3.hashkit.domain.acoustic

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Minimal in-place iterative radix-2 Cooley–Tukey FFT. Pure and dependency-free so the acoustic
 * analysis is unit-testable. Input length must be a power of two.
 */
object Fft {

    /** Largest power of two <= [n]. */
    fun floorPow2(n: Int): Int {
        if (n < 1) return 0
        var p = 1
        while (p * 2 <= n) p *= 2
        return p
    }

    /**
     * Magnitude spectrum of a real signal. Returns the first (size/2) bins; bin k corresponds to
     * frequency k * sampleRate / size. [samples] is truncated to the largest power-of-two length.
     */
    fun magnitudeSpectrum(samples: FloatArray): FloatArray {
        val n = floorPow2(samples.size)
        if (n < 2) return FloatArray(0)
        val re = DoubleArray(n) { samples[it].toDouble() }
        val im = DoubleArray(n)
        // Hann window to reduce spectral leakage.
        for (i in 0 until n) {
            val w = 0.5 * (1 - cos(2.0 * Math.PI * i / (n - 1)))
            re[i] *= w
        }
        transform(re, im)
        val half = n / 2
        return FloatArray(half) { hypot(re[it], im[it]).toFloat() }
    }

    private fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var curR = 1.0
                var curI = 0.0
                for (k in 0 until len / 2) {
                    val aR = re[i + k]
                    val aI = im[i + k]
                    val bR = re[i + k + len / 2] * curR - im[i + k + len / 2] * curI
                    val bI = re[i + k + len / 2] * curI + im[i + k + len / 2] * curR
                    re[i + k] = aR + bR
                    im[i + k] = aI + bI
                    re[i + k + len / 2] = aR - bR
                    im[i + k + len / 2] = aI - bI
                    val nextR = curR * wr - curI * wi
                    curI = curR * wi + curI * wr
                    curR = nextR
                }
                i += len
            }
            len = len shl 1
        }
    }
}
