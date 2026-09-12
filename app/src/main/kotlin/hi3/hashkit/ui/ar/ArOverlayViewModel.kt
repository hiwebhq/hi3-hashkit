package hi3.hashkit.ui.ar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.data.nfc.NfcRouter
import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.AddMinerResult
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.discovery.MinerHostValidator
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.tag.MinerTag
import hi3.hashkit.domain.tag.MinerTagMatcher
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
 * Backs the AR-style overlay. A scanned marker — a QR sticker or an NFC tag, both carrying the
 * same `key=value` payload (name/mac/ip/location) — resolves to a miner whose live telemetry is
 * re-read every poll cycle so the floating card stays current. A tag pointing at a miner not yet
 * in the app can be added by its (private) IP; a tag's Location is shown but never written back.
 */
@HiltViewModel
class ArOverlayViewModel @Inject constructor(
    private val repository: MinerRepository,
    private val pollingEngine: PollingEngine,
    private val nfcRouter: NfcRouter,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Show the camera only when QR is an enabled inventory-tag method; NFC-only skips it. */
    val cameraEnabled: StateFlow<Boolean> =
        settingsRepository.settings
            .map { it.inventoryTagType.showQr }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val matchedId = MutableStateFlow<Long?>(null)

    /** Last scanned payload that didn't match and couldn't be added, for a helpful message. */
    val unmatched = MutableStateFlow<String?>(null)

    /** A scanned tag whose miner isn't known yet but carries a private IP we can offer to add. */
    val pendingAdd = MutableStateFlow<MinerTag?>(null)

    /** Location string from the last matched tag (display only — never written to the miner). */
    val tagLocation = MutableStateFlow<String?>(null)

    /** Transient status (add results, errors). */
    val message = MutableStateFlow<String?>(null)

    /** One-shot confirmation for each discrete NFC tap (shown as a toast). */
    private val _scanToast = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    val scanToast: kotlinx.coroutines.flow.SharedFlow<String> = _scanToast

    @OptIn(ExperimentalCoroutinesApi::class)
    val matched: StateFlow<Miner?> =
        matchedId.flatMapLatest { id ->
            if (id == null) flowOf(null)
            else combine(repository.observeMinerEntity(id), pollingEngine.lastRefresh) { entity, _ ->
                entity?.let { repository.toDomain(it, Instant.now()) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Matched tags go straight to miner detail (handled by the nav host). Only the fallback
        // Overlay case (unknown/addable miner, or no match) reaches the overlay here.
        viewModelScope.launch {
            nfcRouter.target.collect { target ->
                if (target is NfcRouter.Target.Overlay) {
                    // NFC taps are discrete — always re-process, even the same tag again (the
                    // lastScan guard exists only to de-dupe the continuous camera QR stream).
                    lastScan = null
                    onScanned(target.payload, announce = true)
                    nfcRouter.consume()
                }
            }
        }
    }

    /**
     * Resolve a scanned marker (QR text or NFC payload) to a miner. [announce] emits a one-shot
     * toast confirmation — set for discrete NFC taps, off for the continuous camera QR stream.
     */
    fun onScanned(code: String, announce: Boolean = false) {
        val tag = MinerTag.parse(code)
        if (tag == null) {
            // Discrete NFC taps deserve feedback even when the tag isn't ours.
            if (announce) {
                _scanToast.tryEmit(
                    if (code.isBlank()) "Blank NFC tag — write it from a miner's Make tag"
                    else "Not a Hi3 Hashkit tag",
                )
            }
            return
        }
        val dedupe = code.trim()
        if (dedupe.equals(lastScan, ignoreCase = true)) return
        lastScan = dedupe
        viewModelScope.launch {
            val candidates = repository.observeMinerEntities().first().map { e ->
                MinerTagMatcher.Candidate(e.id, e.name, e.host, e.macAddress, e.serialNumber)
            }
            val hit = MinerTagMatcher.match(tag, candidates)
            when {
                hit != null -> {
                    matchedId.value = hit.id
                    tagLocation.value = tag.location
                    unmatched.value = null
                    pendingAdd.value = null
                    if (announce) _scanToast.tryEmit("Scanned ✓ ${hit.name ?: "miner"}")
                }
                // Not known yet, but the tag gives a private IP → offer to add it.
                tag.ip != null && MinerHostValidator.resolvesToAllowed(tag.ip) -> {
                    pendingAdd.value = tag
                    unmatched.value = null
                    if (announce) _scanToast.tryEmit("Scanned tag — ${tag.name ?: tag.ip} not added yet")
                }
                else -> {
                    unmatched.value = tag.rawValue ?: tag.name ?: tag.mac ?: tag.ip ?: dedupe
                    pendingAdd.value = null
                    if (announce) _scanToast.tryEmit("Scanned tag — no matching miner")
                }
            }
        }
    }

    /** Add the pending tag's miner by its IP, then show it. */
    fun addFromTag() {
        val tag = pendingAdd.value ?: return
        val ip = tag.ip ?: return
        viewModelScope.launch {
            message.value = "Adding $ip…"
            when (val r = repository.addByHost(ip)) {
                is AddMinerResult.Added -> {
                    matchedId.value = r.minerId; tagLocation.value = tag.location
                    pendingAdd.value = null; message.value = "Added ${tag.name ?: ip}."
                }
                is AddMinerResult.AlreadyKnown -> {
                    matchedId.value = r.minerId; tagLocation.value = tag.location
                    pendingAdd.value = null; message.value = null
                }
                is AddMinerResult.Unreachable -> message.value = "Couldn't reach $ip: ${r.message}"
                is AddMinerResult.NotSupported -> message.value = r.message
            }
        }
    }

    fun dismissAdd() {
        pendingAdd.value = null
        lastScan = null // allow re-scanning the same tag to retry
    }

    fun clear() {
        matchedId.value = null
        unmatched.value = null
        pendingAdd.value = null
        tagLocation.value = null
        message.value = null
        lastScan = null
    }

    private var lastScan: String? = null
}
