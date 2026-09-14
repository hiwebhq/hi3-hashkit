package hi3.hashkit.domain.solo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeEconomicsTest {

    @Test
    fun `monthly cost and energy scale a day by the average month`() {
        // 15 W at 0.15/kWh: 0.36 kWh/day * 0.15 = 0.054/day.
        val cost = HomeEconomics.monthlyCost(15.0, 0.15)!!
        assertEquals(0.054 * HomeEconomics.DAYS_PER_MONTH, cost, 1e-9)
        assertEquals(0.36 * HomeEconomics.DAYS_PER_MONTH, HomeEconomics.monthlyKwh(15.0)!!, 1e-9)
        assertNull(HomeEconomics.monthlyCost(15.0, 0.0))
        assertNull(HomeEconomics.monthlyCost(null, 0.15))
    }

    @Test
    fun `revenue needs difficulty and a price`() {
        assertNull(HomeEconomics.monthlyRevenue(1000.0, null, 65_000.0))
        assertNull(HomeEconomics.monthlyRevenue(1000.0, 1e14, null))
        val perDay = ProfitMath.revenuePerDay(ProfitMath.btcPerDay(1000.0, 1e14), 65_000.0)!!
        assertEquals(perDay * HomeEconomics.DAYS_PER_MONTH, HomeEconomics.monthlyRevenue(1000.0, 1e14, 65_000.0)!!, 1e-12)
    }

    @Test
    fun `payback is never when the power bill exceeds earnings, months otherwise`() {
        assertTrue(HomeEconomics.payback(200.0, -1.5) is HomeEconomics.Payback.Never)
        assertTrue(HomeEconomics.payback(200.0, 0.0) is HomeEconomics.Payback.Never)
        val months = HomeEconomics.payback(200.0, 4.0) as HomeEconomics.Payback.Months
        assertEquals(50.0, months.months, 1e-9)
    }

    @Test
    fun `payback is unknown without a price or a net figure`() {
        assertNull(HomeEconomics.payback(null, 4.0))
        assertNull(HomeEconomics.payback(0.0, 4.0))
        assertNull(HomeEconomics.payback(200.0, null))
    }
}
