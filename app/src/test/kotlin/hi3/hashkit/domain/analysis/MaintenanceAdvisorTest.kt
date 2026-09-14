package hi3.hashkit.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceAdvisorTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    /** [days] of hourly points ending [endDaysAgo] days before now. */
    private fun week(endDaysAgo: Long, tempC: Double, powerW: Double, days: Long = 7): List<MaintenanceAdvisor.HourPoint> {
        val end = now - endDaysAgo * MaintenanceAdvisor.DAY_MS
        val start = end - days * MaintenanceAdvisor.DAY_MS
        return generateSequence(start) { it + hour }.takeWhile { it < end }
            .map { MaintenanceAdvisor.HourPoint(it, tempC, powerW, onlineSamples = 200) }
            .toList()
    }

    @Test
    fun `a five degree rise at the same power is a dust finding`() {
        val hours = week(28, tempC = 55.0, powerW = 15.0) + week(0, tempC = 60.0, powerW = 15.2)
        val finding = MaintenanceAdvisor.analyze(hours, now)
        assertNotNull(finding)
        assertEquals(5.0, finding!!.tempRiseC, 1e-9)
        assertTrue(finding.message.contains("clean the fan"))
    }

    @Test
    fun `a rise explained by more power is not blamed on dust`() {
        val hours = week(28, tempC = 55.0, powerW = 15.0) + week(0, tempC = 60.0, powerW = 18.0)
        assertNull(MaintenanceAdvisor.analyze(hours, now))
    }

    @Test
    fun `small rises and sparse windows stay quiet`() {
        assertNull(MaintenanceAdvisor.analyze(week(28, 55.0, 15.0) + week(0, 57.0, 15.0), now))
        // Only a day of recent history: not enough hours to judge.
        assertNull(MaintenanceAdvisor.analyze(week(28, 55.0, 15.0) + week(0, 62.0, 15.0, days = 1), now))
        // No baseline at all (miner is new).
        assertNull(MaintenanceAdvisor.analyze(week(0, 62.0, 15.0), now))
    }

    @Test
    fun `offline hours and missing temps are ignored`() {
        val dead = week(28, 55.0, 15.0).map { it.copy(onlineSamples = 0) }
        assertNull(MaintenanceAdvisor.analyze(dead + week(0, 62.0, 15.0), now))
        val noTemp = week(28, 55.0, 15.0).map { it.copy(avgChipTempC = null) }
        assertNull(MaintenanceAdvisor.analyze(noTemp + week(0, 62.0, 15.0), now))
    }
}
