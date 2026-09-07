package hi3.hashkit.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session lock state for the optional app lock. Pure timing logic so the
 * relock-after-background rule is unit-testable; the biometric prompt itself lives
 * in the activity layer.
 */
@Singleton
class AppLockManager @Inject constructor() {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    private var backgroundedAt: Long? = null

    /** Called when the app process starts with the lock enabled. */
    fun lockOnLaunch() {
        _locked.value = true
    }

    fun unlock() {
        _locked.value = false
        backgroundedAt = null
    }

    fun onBackground(nowMs: Long = System.currentTimeMillis()) {
        if (backgroundedAt == null) backgroundedAt = nowMs
    }

    /** Relock when the app was in the background longer than the grace period. */
    fun onForeground(lockEnabled: Boolean, nowMs: Long = System.currentTimeMillis()) {
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

    companion object {
        /** Short trips (share sheet, notification shade, quick app switch) don't relock. */
        const val RELOCK_AFTER_MS = 60_000L
    }
}
