package hi3.hashkit.ui.autotune

import org.junit.Assert.assertEquals
import org.junit.Test

class AutotuneVoltageSnapTest {

    private val volts = listOf(1000, 1060, 1100, 1150, 1200, 1250)

    @Test fun snapsMeasuredVoltageToNearestApproved() {
        // The GammaHex reports a measured ~1245 mV; it must snap to the approved 1250.
        assertEquals(1250, AutotuneViewModel.nearestOption(1245, volts))
        assertEquals(1200, AutotuneViewModel.nearestOption(1205, volts))
        assertEquals(1000, AutotuneViewModel.nearestOption(980, volts))
    }

    @Test fun exactValueIsUnchanged() {
        assertEquals(1150, AutotuneViewModel.nearestOption(1150, volts))
    }

    @Test fun emptyOptionsReturnsInput() {
        assertEquals(1245, AutotuneViewModel.nearestOption(1245, emptyList()))
    }
}
