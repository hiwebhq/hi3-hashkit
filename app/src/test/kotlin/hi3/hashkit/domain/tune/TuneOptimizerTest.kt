package hi3.hashkit.domain.tune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TuneOptimizerTest {

    private fun p(freq: Int, hr: Double?, eff: Double?, power: Double? = null, over: Boolean = false) =
        TuneOptimizer.Point(freq, 1150, hr, power, eff, chipTempC = null, overTemp = over)

    @Test fun picksLowestEfficiencyAndHighestHashrateAmongSafe() {
        val points = listOf(
            p(400, hr = 400.0, eff = 20.0),
            p(500, hr = 520.0, eff = 18.0),
            p(600, hr = 610.0, eff = 22.0),
            p(700, hr = 700.0, eff = 25.0, over = true), // excluded: over temp
        )
        val s = TuneOptimizer.summarize(points)
        assertEquals(500, s.bestEfficiency!!.frequencyMhz)
        assertEquals(600, s.bestHashrate!!.frequencyMhz) // 700 excluded
    }

    @Test fun curveKeepsBestPerFrequency() {
        val points = listOf(
            p(500, hr = 500.0, eff = 19.0),
            p(500, hr = 510.0, eff = 18.0), // better at same freq
            p(400, hr = 400.0, eff = 20.0),
        )
        val curve = TuneOptimizer.summarize(points).efficiencyCurve
        assertEquals(listOf(400, 500), curve.map { it.frequencyMhz })
        assertEquals(18.0, curve.first { it.frequencyMhz == 500 }.efficiencyJTh!!, 0.0001)
    }

    @Test fun invalidPointsAreIgnored() {
        val s = TuneOptimizer.summarize(listOf(p(400, hr = 0.0, eff = null), p(500, hr = null, eff = 18.0)))
        assertNull(s.bestEfficiency)
        assertNull(s.bestHashrate)
    }

    @Test fun bestHashrateUnderPowerCap() {
        val points = listOf(
            p(500, hr = 520.0, eff = 18.0, power = 15.0),
            p(600, hr = 610.0, eff = 22.0, power = 25.0),
        )
        assertEquals(500, TuneOptimizer.bestHashrateUnderPower(points, maxPowerW = 20.0)!!.frequencyMhz)
    }
}
