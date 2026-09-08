package hi3.hashkit.domain.solo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfitMathTest {

    @Test
    fun `btc per day scales with hashrate share of the network`() {
        // 100 TH/s = 100_000 GH/s at a round difficulty.
        val diff = 1e14
        val btc = ProfitMath.btcPerDay(100_000.0, diff)!!
        // blocksPerDay = 1e14_hs?? compute expected: (100000*1e9)/(1e14*2^32)*86400 * 3.125
        val expected = (100_000.0 * 1e9) / (diff * 4_294_967_296.0) * 86_400.0 * 3.125
        assertEquals(expected, btc, expected * 1e-9)
        // Double the hashrate → double the BTC/day.
        assertEquals(btc * 2, ProfitMath.btcPerDay(200_000.0, diff)!!, expected * 1e-9)
    }

    @Test
    fun `cost energy and heat`() {
        assertEquals(7.2, ProfitMath.powerCostPerDay(3000.0, 0.10)!!, 0.001) // 3kW*24*0.10
        assertEquals(72.0, ProfitMath.energyKwhPerDay(3000.0)!!, 0.001)      // 3kW*24
        assertEquals(10236.0, ProfitMath.heatBtuPerHour(3000.0)!!, 0.1)      // 3000*3.412
    }

    @Test
    fun `bad inputs yield null`() {
        assertNull(ProfitMath.btcPerDay(0.0, 1e14))
        assertNull(ProfitMath.btcPerDay(100.0, 0.0))
        assertNull(ProfitMath.powerCostPerDay(3000.0, 0.0))
        assertNull(ProfitMath.energyKwhPerDay(null))
    }
}
