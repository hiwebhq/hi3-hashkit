package hi3.hashkit.data.nfc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries where a scanned Hi3 Hashkit NFC tag should take the user. The OS delivers the tag to
 * [hi3.hashkit.ui.MainActivity] (even from a cold start); it resolves the payload to a miner and
 * publishes a [Target] here, which the nav host acts on.
 *
 * A matched tag goes **straight to that miner's detail** — no camera. Only the edge cases
 * (unknown miner that can be added, or no match) fall back to the AR overlay, which is otherwise
 * the camera/QR experience.
 */
@Singleton
class NfcRouter @Inject constructor() {

    sealed interface Target {
        /** Tag matched a known miner — open the Fleet table and highlight that miner. */
        data class HighlightMiner(val id: Long) : Target
        /** No direct match — hand the raw payload to the AR overlay (add prompt / no-match hint). */
        data class Overlay(val payload: String) : Target
    }

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = _target

    fun emit(target: Target) { _target.value = target }
    fun consume() { _target.value = null }

    /** Miner id to highlight in the Fleet table after a scan (with a nonce so the same miner
     *  scanned again still re-triggers the highlight). */
    private val _highlight = MutableStateFlow<Pair<Long, Long>?>(null)
    val highlight: StateFlow<Pair<Long, Long>?> = _highlight
    private var nonce = 0L

    fun requestHighlight(id: Long) { _highlight.value = id to nonce++ }
    fun clearHighlight() { _highlight.value = null }
}
