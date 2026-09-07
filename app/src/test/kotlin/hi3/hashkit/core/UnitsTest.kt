package hi3.hashkit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitsTest {

    @Test
    fun `hashrate scales across units`() {
        assertEquals("512 GH/s", Units.formatHashrate(512.0))
        assertEquals("1.23 TH/s", Units.formatHashrate(1232.65))
        assertEquals("8.52 TH/s", Units.formatHashrate(8518.31))
        assertEquals("1.00 PH/s", Units.formatHashrate(1_000_000.0))
        assertEquals("500 MH/s", Units.formatHashrate(0.5))
        assertEquals("—", Units.formatHashrate(null))
    }

    @Test
    fun `power formats watts and kilowatts`() {
        assertEquals("14.7 W", Units.formatPower(14.68))
        assertEquals("1.20 kW", Units.formatPower(1200.0))
        assertEquals("—", Units.formatPower(null))
    }

    @Test
    fun `efficiency derives J per TH`() {
        // 14.68 W at 521.79 GH/s ≈ 28.1 J/TH
        assertEquals(28.13, Units.efficiencyJTh(14.68, 521.79)!!, 0.01)
        assertNull(Units.efficiencyJTh(null, 500.0))
        assertNull(Units.efficiencyJTh(10.0, null))
        assertNull(Units.efficiencyJTh(10.0, 0.0))
    }

    @Test
    fun `temperature converts to fahrenheit only on request`() {
        assertEquals("63°C", Units.formatTemp(63.0))
        assertEquals("145°F", Units.formatTemp(63.0, fahrenheit = true))
    }

    @Test
    fun `difficulty uses compact suffixes`() {
        assertEquals("4.29M", Units.formatDifficulty(4_290_000.0))
        assertEquals("7.64G", Units.formatDifficulty(7_640_000_000.0))
        assertEquals("777M", Units.formatDifficulty(777_000_000.0))
        assertEquals("42", Units.formatDifficulty(42.0))
        assertEquals("—", Units.formatDifficulty(null))
    }

    @Test
    fun `uptime formats days hours minutes`() {
        assertEquals("2d 3h", Units.formatUptime(2 * 86400L + 3 * 3600 + 240))
        assertEquals("3h 4m", Units.formatUptime(3 * 3600L + 240))
        assertEquals("4m", Units.formatUptime(240L))
    }
}
