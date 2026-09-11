package hi3.hashkit.domain.acoustic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AcousticAnalyzerTest {

    private fun sine(freqHz: Double, sampleRate: Int, n: Int): FloatArray =
        FloatArray(n) { sin(2.0 * PI * freqHz * it / sampleRate).toFloat() }

    @Test fun floorPow2Works() {
        assertEquals(8192, Fft.floorPow2(9000))
        assertEquals(8, Fft.floorPow2(8))
        assertEquals(0, Fft.floorPow2(0))
    }

    @Test fun detectsDominantToneFrequency() {
        val sr = 8192
        val r = AcousticAnalyzer.analyze(sine(120.0, sr, 8192), sr)
        assertNotNull(r)
        // hzPerBin = 1.0 → dominant should be within a couple of bins of 120 Hz.
        assertTrue("dominant ${r!!.dominantHz}", kotlin.math.abs(r.dominantHz - 120.0) <= 3.0)
        assertTrue("tonalRatio ${r.tonalRatio}", r.tonalRatio > 12.0)
    }

    @Test fun lowStrongToneFlagsImbalance() {
        val sr = 8192
        val r = AcousticAnalyzer.analyze(sine(45.0, sr, 8192), sr)!!
        assertTrue(r.findings.any { it.contains("imbalance", ignoreCase = true) })
        assertTrue(!r.healthy)
    }

    @Test fun highFrequencyToneFlagsBearing() {
        val sr = 8192
        val r = AcousticAnalyzer.analyze(sine(3000.0, sr, 8192), sr)!!
        assertTrue("highFreqRatio ${r.highFreqRatio}", r.highFreqRatio > 0.35)
        assertTrue(r.findings.any { it.contains("bearing", ignoreCase = true) })
    }

    @Test fun tooShortReturnsNull() {
        assertEquals(null, AcousticAnalyzer.analyze(FloatArray(4), 8192))
    }
}
