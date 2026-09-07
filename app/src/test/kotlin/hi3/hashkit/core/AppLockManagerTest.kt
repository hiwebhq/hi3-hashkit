package hi3.hashkit.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockManagerTest {

    @Test
    fun `locks on launch and unlocks on auth`() {
        val lock = AppLockManager()
        lock.lockOnLaunch()
        assertTrue(lock.locked.value)
        lock.unlock()
        assertFalse(lock.locked.value)
    }

    @Test
    fun `short background trips do not relock`() {
        val lock = AppLockManager()
        lock.unlock()
        lock.onBackground(nowMs = 1_000)
        lock.onForeground(lockEnabled = true, nowMs = 1_000 + AppLockManager.RELOCK_AFTER_MS - 1)
        assertFalse(lock.locked.value)
    }

    @Test
    fun `long background relocks when enabled`() {
        val lock = AppLockManager()
        lock.unlock()
        lock.onBackground(nowMs = 1_000)
        lock.onForeground(lockEnabled = true, nowMs = 1_000 + AppLockManager.RELOCK_AFTER_MS)
        assertTrue(lock.locked.value)
    }

    @Test
    fun `disabled lock never relocks and clears a stale lock`() {
        val lock = AppLockManager()
        lock.lockOnLaunch()
        lock.onBackground(nowMs = 0)
        lock.onForeground(lockEnabled = false, nowMs = 10 * AppLockManager.RELOCK_AFTER_MS)
        assertFalse(lock.locked.value)
    }

    @Test
    fun `first background timestamp wins across repeated stop events`() {
        val lock = AppLockManager()
        lock.unlock()
        lock.onBackground(nowMs = 1_000)
        lock.onBackground(nowMs = 50_000) // second ON_STOP must not reset the clock
        lock.onForeground(lockEnabled = true, nowMs = 1_000 + AppLockManager.RELOCK_AFTER_MS)
        assertTrue(lock.locked.value)
    }
}
