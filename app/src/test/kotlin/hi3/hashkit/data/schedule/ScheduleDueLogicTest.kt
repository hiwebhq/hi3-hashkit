package hi3.hashkit.data.schedule

import hi3.hashkit.data.db.ScheduleEntity
import hi3.hashkit.data.repo.BulkAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ScheduleDueLogicTest {

    private val zone: ZoneId = ZoneOffset.UTC

    private fun schedule(
        enabled: Boolean = true,
        timeMinutes: Int = 3 * 60, // 03:00
        days: String = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
        lastRunAt: Long? = null,
        minInterval: Int = 60,
        actionType: String = "reboot",
        params: String = "{}",
    ) = ScheduleEntity(
        id = 1, enabled = enabled, label = "t", actionType = actionType, paramsJson = params,
        targetMinerIdsCsv = "", targetGroup = null, timeMinutesOfDay = timeMinutes,
        daysOfWeekCsv = days, minIntervalMinutes = minInterval,
        lastRunAtEpochMs = lastRunAt, lastResult = null,
    )

    // 2026-09-07 is a Monday.
    private fun monday(hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 9, 7, hour, minute)

    private fun epochOf(dt: LocalDateTime): Long = dt.toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `due after the scheduled time on a selected day`() {
        assertTrue(ScheduleDueLogic.isDue(schedule(), monday(3, 5), zone))
        assertFalse(ScheduleDueLogic.isDue(schedule(), monday(2, 55), zone))
    }

    @Test
    fun `disabled schedules never fire`() {
        assertFalse(ScheduleDueLogic.isDue(schedule(enabled = false), monday(4), zone))
    }

    @Test
    fun `not due on unselected days`() {
        assertFalse(
            ScheduleDueLogic.isDue(schedule(days = "TUESDAY"), monday(4), zone),
        )
        assertTrue(
            ScheduleDueLogic.isDue(schedule(days = "MONDAY"), monday(4), zone),
        )
    }

    @Test
    fun `runs once per window - already ran today`() {
        val ranAt = epochOf(monday(3, 1))
        assertFalse(ScheduleDueLogic.isDue(schedule(lastRunAt = ranAt), monday(9), zone))
    }

    @Test
    fun `missed window fires later the same day but not the next morning before time`() {
        // Ran Sunday; Monday 07:00 (device was asleep at 03:00) -> still fires.
        val sundayRun = epochOf(monday(3).minusDays(1))
        assertTrue(ScheduleDueLogic.isDue(schedule(lastRunAt = sundayRun), monday(7), zone))
    }

    @Test
    fun `min interval guards across windows`() {
        // Ran at 23:30 yesterday; a 02:00-scheduled task at 02:05 with 6h min interval waits.
        val lateRun = epochOf(monday(2, 5).minusHours(2).minusMinutes(35)) // Sunday 23:30
        val s = schedule(timeMinutes = 2 * 60, lastRunAt = lateRun, minInterval = 360)
        assertFalse(ScheduleDueLogic.isDue(s, monday(2, 5), zone))
        assertTrue(ScheduleDueLogic.isDue(s, monday(6, 0), zone))
    }

    @Test
    fun `action parsing maps types and rejects invalid params`() {
        assertEquals(BulkAction.Reboot, ScheduleDueLogic.actionOf(schedule()))

        val pool = ScheduleDueLogic.actionOf(
            schedule(actionType = "set_pool", params = """{"url":"p.example","port":3333,"worker":"w"}"""),
        )
        assertEquals(BulkAction.SetPool("p.example", 3333, "w"), pool)

        assertNull(ScheduleDueLogic.actionOf(schedule(actionType = "set_pool", params = "{}")))
        assertNull(ScheduleDueLogic.actionOf(schedule(actionType = "explode", params = "{}")))

        val fan = ScheduleDueLogic.actionOf(
            schedule(actionType = "set_fan", params = """{"auto":false,"percent":65}"""),
        )
        assertTrue(fan is BulkAction.SetFan)
    }
}
