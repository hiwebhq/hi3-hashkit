package hi3.hashkit.domain.curtail

import org.junit.Assert.assertEquals
import org.junit.Test

class CurtailmentEngineTest {

    // Solar: higher export favours running. resume >= 500, curtail <= 0.
    @Test fun solarResumesWhenExportHigh() {
        assertEquals(
            CurtailmentEngine.Action.RUN,
            CurtailmentEngine.decide(600.0, running = false, resumeThreshold = 500.0, curtailThreshold = 0.0, favourRunWhenAbove = true),
        )
    }

    @Test fun solarCurtailsWhenImporting() {
        assertEquals(
            CurtailmentEngine.Action.CURTAIL,
            CurtailmentEngine.decide(-100.0, running = true, resumeThreshold = 500.0, curtailThreshold = 0.0, favourRunWhenAbove = true),
        )
    }

    @Test fun solarHoldsInDeadband() {
        // 200 W: above curtail(0) but below resume(500) → hold in both states.
        assertEquals(
            CurtailmentEngine.Action.HOLD,
            CurtailmentEngine.decide(200.0, running = true, resumeThreshold = 500.0, curtailThreshold = 0.0, favourRunWhenAbove = true),
        )
        assertEquals(
            CurtailmentEngine.Action.HOLD,
            CurtailmentEngine.decide(200.0, running = false, resumeThreshold = 500.0, curtailThreshold = 0.0, favourRunWhenAbove = true),
        )
    }

    // Price: higher price favours curtailing. resume <= 15, curtail >= 30.
    @Test fun priceCurtailsWhenExpensive() {
        assertEquals(
            CurtailmentEngine.Action.CURTAIL,
            CurtailmentEngine.decide(35.0, running = true, resumeThreshold = 15.0, curtailThreshold = 30.0, favourRunWhenAbove = false),
        )
    }

    @Test fun priceResumesWhenCheap() {
        assertEquals(
            CurtailmentEngine.Action.RUN,
            CurtailmentEngine.decide(10.0, running = false, resumeThreshold = 15.0, curtailThreshold = 30.0, favourRunWhenAbove = false),
        )
    }

    @Test fun priceHoldsInDeadband() {
        assertEquals(
            CurtailmentEngine.Action.HOLD,
            CurtailmentEngine.decide(20.0, running = true, resumeThreshold = 15.0, curtailThreshold = 30.0, favourRunWhenAbove = false),
        )
    }

    @Test fun targetRunningIgnoresCurrentState() {
        assertEquals(true, CurtailmentEngine.targetRunning(600.0, 500.0, 0.0, favourRunWhenAbove = true))
        assertEquals(false, CurtailmentEngine.targetRunning(35.0, 15.0, 30.0, favourRunWhenAbove = false))
    }
}
