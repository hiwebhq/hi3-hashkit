package hi3.hashkit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HalvingMathTest {

    @Test fun nextHalvingBlockAndEra() {
        // Block 840,000 was the 4th halving boundary (era 4 begins).
        assertEquals(1_050_000L, HalvingMath.nextHalvingBlock(840_000))
        assertEquals(1_050_000L, HalvingMath.nextHalvingBlock(900_000))
        assertEquals(4, HalvingMath.halvingEra(900_000))
    }

    @Test fun subsidyMatchesEra() {
        assertEquals(50.0, HalvingMath.subsidyBtc(0), 1e-9)
        assertEquals(6.25, HalvingMath.subsidyBtc(700_000), 1e-9)   // era 3
        assertEquals(3.125, HalvingMath.subsidyBtc(900_000), 1e-9)  // era 4
    }

    @Test fun blocksToHalvingCountsDown() {
        assertEquals(150_000L, HalvingMath.blocksToHalving(900_000))
    }

    @Test fun humanDurationBuckets() {
        assertEquals("now", HalvingMath.humanDuration(0))
        assertEquals("45m", HalvingMath.humanDuration(45 * 60_000L))
        assertEquals("3h 0m", HalvingMath.humanDuration(3 * 3_600_000L))
        assertEquals("2d 3h", HalvingMath.humanDuration((2 * 24 + 3) * 3_600_000L))
        assertEquals("412d", HalvingMath.humanDuration(412L * 24 * 3_600_000L))
    }
}
