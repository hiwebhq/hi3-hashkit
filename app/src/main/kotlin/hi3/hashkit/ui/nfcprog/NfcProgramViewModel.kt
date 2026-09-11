package hi3.hashkit.ui.nfcprog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.tag.MinerTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** One miner to program a tag for, with its canonical payload. */
data class NfcTarget(
    val id: Long,
    val name: String,
    val mac: String?,
    val ip: String?,
    val location: String?,
    val payload: String,
)

data class NfcProgramState(
    val targets: List<NfcTarget> = emptyList(),
    val index: Int = 0,
    val written: Set<Long> = emptySet(),
    val loading: Boolean = true,
    val message: String? = null,
    val showQr: Boolean = false,
) {
    val current: NfcTarget? get() = targets.getOrNull(index)
    val total: Int get() = targets.size
    val writtenCount: Int get() = written.size
    val allDone: Boolean get() = targets.isNotEmpty() && written.size == targets.size
}

/**
 * Drives the guided NFC-tag programming sequence. The set of miners is passed in from the Fleet
 * table (exactly the rows currently filtered/sorted) via the `ids` nav arg, or — when empty —
 * the whole fleet. Writing the tag itself happens in the screen (it needs the live NFC Tag); the
 * VM only tracks progress and the payload for each miner.
 */
@HiltViewModel
class NfcProgramViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MinerRepository,
) : ViewModel() {

    private val idOrder: List<Long> =
        savedStateHandle.get<String>("ids").orEmpty()
            .split(',').mapNotNull { it.trim().toLongOrNull() }

    private val _state = MutableStateFlow(NfcProgramState())
    val state: StateFlow<NfcProgramState> = _state

    init {
        viewModelScope.launch {
            val now = Instant.now()
            val all = repository.observeMinerEntities().first()
                .filter { !it.isDemo }
                .map { repository.toDomain(it, now) }
            val ordered = if (idOrder.isEmpty()) {
                all.sortedBy { it.name.lowercase() }
            } else {
                val byId = all.associateBy { it.id }
                idOrder.mapNotNull { byId[it] }
            }
            val targets = ordered.map { m ->
                NfcTarget(
                    id = m.id, name = m.name, mac = m.identity.macAddress, ip = m.host, location = m.location,
                    payload = MinerTag.encode(m.name, m.identity.macAddress, m.host, m.location),
                )
            }
            _state.value = _state.value.copy(
                targets = targets, loading = false,
                message = if (targets.isEmpty()) "No miners to program." else null,
            )
        }
    }

    fun goTo(i: Int) {
        if (i in _state.value.targets.indices) _state.value = _state.value.copy(index = i, message = null)
    }

    fun next() = goTo((_state.value.index + 1).coerceAtMost(_state.value.targets.lastIndex))
    fun prev() = goTo((_state.value.index - 1).coerceAtLeast(0))

    fun skip() {
        val s = _state.value
        if (s.index < s.targets.lastIndex) goTo(s.index + 1)
    }

    /** Record that the current miner's tag was written, and advance to the next unwritten one. */
    fun markWritten(id: Long) {
        val s = _state.value
        val written = s.written + id
        val nextUnwritten = ((s.index + 1)..s.targets.lastIndex)
            .firstOrNull { s.targets[it].id !in written }
            ?: (0 until s.index).firstOrNull { s.targets[it].id !in written }
        _state.value = s.copy(
            written = written,
            index = nextUnwritten ?: s.index,
            message = "Wrote ${s.targets.firstOrNull { it.id == id }?.name ?: "tag"}." +
                if (written.size == s.targets.size) " All ${s.targets.size} done." else "",
        )
    }

    fun setMessage(msg: String?) { _state.value = _state.value.copy(message = msg) }
    fun toggleQr() { _state.value = _state.value.copy(showQr = !_state.value.showQr) }
}
