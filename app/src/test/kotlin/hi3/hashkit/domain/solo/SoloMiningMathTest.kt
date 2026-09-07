package hi3.hashkit.domain.solo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SoloMiningMathTest {

    // Reference: at difficulty D, expected hashes per block = D * 2^32.

    @Test
    fun `expected blocks matches closed form`() {
        // 1 TH/s for 1 day at difficulty 100e12:
        // 1e12 * 86400 / (100e12 * 2^32) = 8.64e16 / 4.295e26 ≈ 2.0117e-10
        val lambda = SoloMiningMath.expectedBlocks(1000.0, 100e12, 86_400.0)
        assertEquals(2.0117e-10, lambda, 1e-13)
    }

    @Test
    fun `probability approximates lambda for tiny lambda`() {
        val hash = 10_000.0 // 10 TH/s
        val diff = 120e12
        val p = SoloMiningMath.probabilityAtLeastOneBlock(hash, diff, 86_400.0)
        val lambda = SoloMiningMath.expectedBlocks(hash, diff, 86_400.0)
        // For lambda << 1, P ≈ lambda.
        assertTrue(abs(p - lambda) / lambda < 1e-6)
    }

    @Test
    fun `probability approaches 1 for huge hashrate`() {
        val p = SoloMiningMath.probabilityAtLeastOneBlock(1e12, 1.0, 86_400.0)
        assertTrue(p > 0.999999)
    }

    @Test
    fun `probability is monotonic in time`() {
        val d = 120e12
        val day = SoloMiningMath.probabilityAtLeastOneBlock(5000.0, d, 86_400.0)
        val week = SoloMiningMath.probabilityAtLeastOneBlock(5000.0, d, 7 * 86_400.0)
        val year = SoloMiningMath.probabilityAtLeastOneBlock(5000.0, d, 365.25 * 86_400.0)
        assertTrue(day < week && week < year)
        // One week ≈ 7 independent days for tiny p.
        assertEquals(7.0, week / day, 0.01)
    }

    @Test
    fun `expected time inverts expected blocks`() {
        val secs = SoloMiningMath.expectedSecondsPerBlock(1000.0, 100e12)!!
        val blocksInThatTime = SoloMiningMath.expectedBlocks(1000.0, 100e12, secs)
        assertEquals(1.0, blocksInThatTime, 1e-9)
    }

    @Test
    fun `invalid inputs return zero or null`() {
        assertEquals(0.0, SoloMiningMath.probabilityAtLeastOneBlock(0.0, 100e12, 86_400.0), 0.0)
        assertNull(SoloMiningMath.expectedSecondsPerBlock(0.0, 100e12))
        assertNull(SoloMiningMath.secondsToReachProbability(1000.0, 100e12, 1.5))
    }
}
