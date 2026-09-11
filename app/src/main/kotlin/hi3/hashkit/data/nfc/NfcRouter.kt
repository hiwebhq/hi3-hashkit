package hi3.hashkit.data.nfc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries a miner-tag payload that the OS delivered to [hi3.hashkit.ui.MainActivity] via an NFC
 * intent (a Hi3 Hashkit tag was scanned) to whatever screen consumes it — the AR overlay. This is
 * how tags open the app: Android dispatches the tag to MainActivity (even from a cold start), which
 * publishes the payload here; the app then routes to the AR overlay, which resolves the miner.
 */
@Singleton
class NfcRouter @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun emit(payload: String) { _pending.value = payload }
    fun consume() { _pending.value = null }
}
