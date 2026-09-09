package hi3.hashkit.domain.alerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationWindowTest {

    @Test fun sameDayWindow() {
        // 09:00–17:00
        assertTrue(NotificationWindow.isQuiet(10 * 60, 9 * 60, 17 * 60))
        assertFalse(NotificationWindow.isQuiet(8 * 60, 9 * 60, 17 * 60))
        assertFalse(NotificationWindow.isQuiet(17 * 60, 9 * 60, 17 * 60)) // end exclusive
    }

    @Test fun windowWrappingMidnight() {
        // 22:00–07:00
        assertTrue(NotificationWindow.isQuiet(23 * 60, 22 * 60, 7 * 60))
        assertTrue(NotificationWindow.isQuiet(2 * 60, 22 * 60, 7 * 60))
        assertFalse(NotificationWindow.isQuiet(12 * 60, 22 * 60, 7 * 60))
        assertTrue(NotificationWindow.isQuiet(22 * 60, 22 * 60, 7 * 60)) // start inclusive
        assertFalse(NotificationWindow.isQuiet(7 * 60, 22 * 60, 7 * 60)) // end exclusive
    }

    @Test fun emptyWindowIsNeverQuiet() {
        assertFalse(NotificationWindow.isQuiet(0, 8 * 60, 8 * 60))
    }

    @Test fun digestFiresOnceAfterTheHour() {
        val today = 1_000_000L
        assertFalse(NotificationWindow.digestDue(today - 1, today, 0)) // before the hour
        assertTrue(NotificationWindow.digestDue(today + 1, today, 0)) // reached, never sent
        assertFalse(NotificationWindow.digestDue(today + 1, today, today)) // already sent today
    }
}
