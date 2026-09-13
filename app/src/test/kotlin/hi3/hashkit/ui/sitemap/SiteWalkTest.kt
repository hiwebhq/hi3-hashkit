package hi3.hashkit.ui.sitemap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteWalkTest {

    private val cfg = SiteMapConfig(buildings = 2, racksPerBuilding = 3, tiersPerRack = 4, positionsPerTier = 5)

    @Test
    fun `walk always starts at B1-R1-T1-P1`() {
        assertEquals(Slot(1, 1, 1, 1), SiteWalk.slotAt(cfg, 0))
    }

    @Test
    fun `positions fill left to right before the tier advances`() {
        assertEquals(Slot(1, 1, 1, 2), SiteWalk.slotAt(cfg, 1))
        assertEquals(Slot(1, 1, 1, 5), SiteWalk.slotAt(cfg, 4))
        assertEquals(Slot(1, 1, 2, 1), SiteWalk.slotAt(cfg, 5)) // next tier up, back to the left end
    }

    @Test
    fun `rack advances after the top tier, building after the last rack`() {
        assertEquals(Slot(1, 1, 4, 5), SiteWalk.slotAt(cfg, cfg.slotsPerRack - 1))
        assertEquals(Slot(1, 2, 1, 1), SiteWalk.slotAt(cfg, cfg.slotsPerRack))
        assertEquals(Slot(1, 3, 4, 5), SiteWalk.slotAt(cfg, cfg.slotsPerBuilding - 1))
        assertEquals(Slot(2, 1, 1, 1), SiteWalk.slotAt(cfg, cfg.slotsPerBuilding))
    }

    @Test
    fun `slotAt and indexOf round-trip over the whole site`() {
        for (index in 0 until cfg.totalSlots) {
            val slot = SiteWalk.slotAt(cfg, index)!!
            assertEquals(index, SiteWalk.indexOf(cfg, slot))
        }
    }

    @Test
    fun `out-of-range indexes return null`() {
        assertNull(SiteWalk.slotAt(cfg, -1))
        assertNull(SiteWalk.slotAt(cfg, cfg.totalSlots))
    }

    @Test
    fun `totals and validity`() {
        assertEquals(20, cfg.slotsPerRack)
        assertEquals(60, cfg.slotsPerBuilding)
        assertEquals(120, cfg.totalSlots)
        assertTrue(cfg.isValid)
        assertEquals(false, cfg.copy(tiersPerRack = 0).isValid)
    }

    @Test
    fun `location code matches the format the Rack screen groups by`() {
        assertEquals("B2-R3-T1-P4", Slot(2, 3, 1, 4).code)
    }

    @Test
    fun `csv has a header and one row per captured slot in walk order`() {
        val rows = listOf(
            CapturedSlot(Slot(1, 1, 1, 1), "10.0.0.48", "AA:BB:CC:DD:EE:FF", manual = false, atEpochMs = 1000L),
            CapturedSlot(Slot(1, 1, 1, 2), "10.0.0.107", null, manual = true, atEpochMs = 2000L),
        )
        val lines = siteMapCsv(rows).trim().lines()
        assertEquals(3, lines.size)
        assertEquals("building,rack,tier,position,location,ip,last_octet,mac,source,captured_at_epoch_ms", lines[0])
        assertEquals("1,1,1,1,B1-R1-T1-P1,10.0.0.48,48,AA:BB:CC:DD:EE:FF,ip-report,1000", lines[1])
        assertEquals("1,1,1,2,B1-R1-T1-P2,10.0.0.107,107,,manual,2000", lines[2])
    }
}
