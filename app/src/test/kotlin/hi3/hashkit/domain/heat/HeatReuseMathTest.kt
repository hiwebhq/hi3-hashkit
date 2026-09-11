package hi3.hashkit.domain.heat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeatReuseMathTest {

    @Test fun btuPerHourUsesConversionFactor() {
        assertEquals(3412.0, HeatReuseMath.btuPerHour(1000.0)!!, 0.001)
        assertNull(HeatReuseMath.btuPerHour(0.0))
        assertNull(HeatReuseMath.btuPerHour(null))
    }

    @Test fun kwhThermalPerDayIsPowerTimes24() {
        // 1000 W for 24 h = 24 kWh.
        assertEquals(24.0, HeatReuseMath.kwhThermalPerDay(1000.0)!!, 0.001)
    }

    @Test fun heatingValueEqualsResistiveElectricityCost() {
        // 1000 W → 24 kWh/day; at 0.30/kWh that heat is worth 7.20/day.
        assertEquals(7.20, HeatReuseMath.heatingValuePerDay(1000.0, 0.30)!!, 0.0001)
        assertNull(HeatReuseMath.heatingValuePerDay(1000.0, 0.0))
        assertNull(HeatReuseMath.heatingValuePerDay(1000.0, null))
    }

    @Test fun monthlyIsDailyTimesAverageMonth() {
        val daily = HeatReuseMath.heatingValuePerDay(1000.0, 0.30)!!
        assertEquals(daily * HeatReuseMath.DAYS_PER_MONTH, HeatReuseMath.heatingValuePerMonth(1000.0, 0.30)!!, 0.001)
    }

    @Test fun heatPumpOffsetIsDividedByCop() {
        val monthly = HeatReuseMath.heatingValuePerMonth(1000.0, 0.30)!!
        assertEquals(monthly / 3.0, HeatReuseMath.heatingValuePerMonthVsHeatPump(1000.0, 0.30, 3.0)!!, 0.001)
        assertNull(HeatReuseMath.heatingValuePerMonthVsHeatPump(1000.0, 0.30, 0.0))
    }
}
