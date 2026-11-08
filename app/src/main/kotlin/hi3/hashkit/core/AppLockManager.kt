package hi3.hashkit.core

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session lock state for the optional app lock. Pure timing logic so the
 * relock-after-background rule is unit-testable; the biometric prompt itself lives
 * in the activity layer.
 *
 * Time is measured with a monotonic clock ([SystemClock.elapsedRealtime]) so winding the
 * wall clock back while the app is in the background cannot defeat the relock.
 */
@Singleton
class AppLockManager @Inject constructor() {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    private var backgroundedAt: Long? = null

    /**
     * True once an Activity has applied the launch lock in this process. A fresh process
     * (including one Android recreated behind the user's back, with saved instance state)
     * starts with this false, so the launch lock is applied regardless of saved state.
     */
    @Volatile var launchHandled: Boolean = false
        private set

    /** Called when the app process starts with the lock enabled. */
    fun lockOnLaunch() {
        launchHandled = true
        _locked.value = true
    }

    /** Record that this process has made its launch decision without locking (lock disabled). */
    fun markLaunchHandled() {
        launchHandled = true
    }

    fun unlock() {
        _locked.value = false
        backgroundedAt = null
    }

    fun onBackground(nowMs: Long = now()) {
        if (backgroundedAt == null) backgroundedAt = nowMs
    }

    /** Relock when the app was in the background longer than the grace period. */
    fun onForeground(lockEnabled: Boolean, nowMs: Long = now()) {
        val since = backgroundedAt
        backgroundedAt = null
        if (!lockEnabled) {
            _locked.value = false
            return
        }
        if (since != null && nowMs - since >= RELOCK_AFTER_MS) {
            _locked.value = true
        }
    }

    private fun now(): Long =
        runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.nanoTime() / NANOS_PER_MS }

    companion object {
        private const val NANOS_PER_MS = 1_000_000L
        /** Short trips (share sheet, notification shade, quick app switch) don't relock. */
        const val RELOCK_AFTER_MS = 60_000L
    }
}
