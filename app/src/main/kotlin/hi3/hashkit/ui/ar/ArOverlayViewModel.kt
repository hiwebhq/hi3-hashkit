package hi3.hashkit.ui.ar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.model.Miner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Backs the AR-style overlay: a scanned marker (QR/ID sticker) resolves to a miner, whose live
 * telemetry is then re-read every poll cycle so the floating card stays current.
 */
@HiltViewModel
class ArOverlayViewModel @Inject constructor(
    private val repository: MinerRepository,
    private val pollingEngine: PollingEngine,
) : ViewModel() {

    private val matchedId = MutableStateFlow<Long?>(null)

    /** Last scanned code that didn't match any miner, for a helpful message. */
    val unmatched = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val matched: StateFlow<Miner?> =
        matchedId.flatMapLatest { id ->
            if (id == null) flowOf(null)
            else combine(repository.observeMinerEntity(id), pollingEngine.lastRefresh) { entity, _ ->
                entity?.let { repository.toDomain(it, Instant.now()) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Resolve a scanned sticker to a miner. Returns true if it matched. */
    fun onScanned(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || trimmed.equals(lastScan, ignoreCase = true)) return
        lastScan = trimmed
        viewModelScope.launch {
            val entities = repository.observeMinerEntities().first()
            val key = trimmed.removePrefix("hi3miner:").removePrefix("hi3:").trim()
            val normMac = key.replace(":", "").replace("-", "").lowercase()
            val match = entities.firstOrNull { e ->
                val id = repository.toDomain(e, Instant.now())
                key.toLongOrNull()?.let { it == e.id } == true ||
                    id.name.equals(key, ignoreCase = true) ||
                    id.host.equals(key, ignoreCase = true) ||
                    id.identity.macAddress?.replace(":", "")?.replace("-", "")?.lowercase() == normMac ||
                    id.identity.serialNumber?.equals(key, ignoreCase = true) == true
            }
            if (match != null) {
                matchedId.value = match.id
                unmatched.value = null
            } else {
                unmatched.value = trimmed
            }
        }
    }

    fun clear() {
        matchedId.value = null
        unmatched.value = null
        lastScan = null
    }

    private var lastScan: String? = null
}
