package hi3.hashkit.domain.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertOverridesTest {

    private val global = AlertThresholds(
        hashrateBelowPercent = 80.0,
        chipTempC = 70.0,
        vrTempC = 90.0,
        rejectRatePercent = 3.0,
    )

    @Test
    fun `null overrides keep global values`() {
        assertEquals(global, global.withOverrides(null))
        assertEquals(global, global.withOverrides(AlertOverrides()))
    }

    @Test
    fun `set fields override and unset fields fall back`() {
        val effective = global.withOverrides(
            AlertOverrides(chipTempC = 62.0, rejectRatePercent = 1.0),
        )
        assertEquals(62.0, effective.chipTempC, 0.0)
        assertEquals(1.0, effective.rejectRatePercent, 0.0)
        assertEquals(80.0, effective.hashrateBelowPercent, 0.0)
        assertEquals(90.0, effective.vrTempC, 0.0)
        // Cooldown and offline behavior are never per-miner.
        assertEquals(global.cooldownMs, effective.cooldownMs)
        assertEquals(global.offlineAfterFailures, effective.offlineAfterFailures)
    }

    @Test
    fun `isEmpty reflects whether anything is customized`() {
        assertTrue(AlertOverrides().isEmpty)
        assertFalse(AlertOverrides(chipTempC = 60.0).isEmpty)
        assertFalse(AlertOverrides(muted = true).isEmpty)
    }
}
