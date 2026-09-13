package hi3.hashkit.domain.tune

import hi3.hashkit.data.db.SettingsPeriodStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TuneInsightsTest {

    private fun stat(
        mhz: Double, mv: Double, samples: Int = 100,
        rate: Double? = 1000.0, eff: Double? = 20.0, maxTemp: Double? = 60.0,
    ) = SettingsPeriodStat(mhz, mv, samples, rate, 25.0, eff, maxTemp)

    @Test
    fun `best picks respect the temp limit`() {
        val cool = stat(600.0, 1100.0, rate = 900.0, eff = 18.0, maxTemp = 58.0)
        val fastButHot = stat(800.0, 1250.0, rate = 1400.0, eff = 22.0, maxTemp = 78.0)
        val ranked = TuneInsights.rank(listOf(cool, fastButHot), tempLimitC = 70.0)
        assertEquals(cool, ranked.bestEfficiency)
        assertEquals(cool, ranked.bestHashrate) // the hot point is disqualified
        assertEquals(2, ranked.periods.size) // but still listed
    }

    @Test
    fun `without a temp limit the fastest point wins best rate`() {
        val cool = stat(600.0, 1100.0, rate = 900.0, eff = 18.0)
        val fast = stat(800.0, 1250.0, rate = 1400.0, eff = 22.0, maxTemp = 78.0)
        val ranked = TuneInsights.rank(listOf(cool, fast), tempLimitC = null)
        assertEquals(fast, ranked.bestHashrate)
        assertEquals(cool, ranked.bestEfficiency)
    }

    @Test
    fun `peer gap is percent vs median`() {
        // Median of [900, 1000, 1100] = 1000; mine = 880 -> -12%.
        assertEquals(-12.0, TuneInsights.peerGapPercent(880.0, listOf(1100.0, 900.0, 1000.0))!!, 0.01)
        // Even peer count uses the midpoint average.
        assertEquals(10.0, TuneInsights.peerGapPercent(1100.0, listOf(900.0, 1100.0))!!, 0.01)
    }

    @Test
    fun `peer gap is null without data`() {
        assertNull(TuneInsights.peerGapPercent(null, listOf(1000.0)))
        assertNull(TuneInsights.peerGapPercent(1000.0, emptyList()))
        assertNull(TuneInsights.peerGapPercent(0.0, listOf(1000.0)))
    }
}
