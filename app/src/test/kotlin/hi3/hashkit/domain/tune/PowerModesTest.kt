package hi3.hashkit.domain.tune

import hi3.hashkit.domain.adapter.TuneOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerModesTest {

    // A real Bitaxe Gamma's /api/system/asic option lists.
    private val gamma = TuneOptions(
        frequencyOptionsMhz = listOf(400, 490, 525, 550, 600, 625),
        voltageOptionsMv = listOf(1000, 1060, 1100, 1150, 1200, 1250),
        defaultFrequencyMhz = 525,
        defaultVoltageMv = 1150,
    )

    @Test
    fun `modes map onto approved values at stock voltage`() {
        assertEquals(PowerModes.Point(400, 1150), PowerModes.resolve(gamma, PowerMode.QUIET))
        assertEquals(PowerModes.Point(525, 1150), PowerModes.resolve(gamma, PowerMode.NORMAL))
        assertEquals(PowerModes.Point(600, 1150), PowerModes.resolve(gamma, PowerMode.BOOST))
    }

    @Test
    fun `boost is capped at the highest approved frequency`() {
        val nearTop = gamma.copy(defaultFrequencyMhz = 600)
        assertEquals(PowerModes.Point(625, 1150), PowerModes.resolve(nearTop, PowerMode.BOOST))
    }

    @Test
    fun `missing defaults fall back to the middle of the lists or the current voltage`() {
        val noDefaults = gamma.copy(defaultFrequencyMhz = null, defaultVoltageMv = null)
        assertEquals(PowerModes.Point(550, 1150), PowerModes.resolve(noDefaults, PowerMode.NORMAL))
        assertEquals(PowerModes.Point(550, 1200), PowerModes.resolve(noDefaults, PowerMode.NORMAL, currentVoltageMv = 1200))
        // A current voltage the firmware doesn't list is ignored, never applied.
        assertEquals(PowerModes.Point(550, 1150), PowerModes.resolve(noDefaults, PowerMode.NORMAL, currentVoltageMv = 1337))
        assertNull(PowerModes.resolve(gamma.copy(frequencyOptionsMhz = emptyList()), PowerMode.QUIET))
    }

    @Test
    fun `current point is recognised as a mode or reported custom`() {
        assertEquals(PowerMode.QUIET, PowerModes.modeOf(gamma, 400, 1150))
        assertEquals(PowerMode.BOOST, PowerModes.modeOf(gamma, 600, 1150))
        assertNull(PowerModes.modeOf(gamma, 600, 1200)) // boosted at raised voltage: custom
        assertNull(PowerModes.modeOf(gamma, null, 1150))
    }
}
