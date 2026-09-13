package hi3.hashkit.ui.sitemap

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import hi3.hashkit.adapters.cgminer.GenericCgMinerAdapter
import hi3.hashkit.data.db.FarmEntity
import hi3.hashkit.data.repo.AddMinerResult
import hi3.hashkit.data.repo.FarmRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.discovery.IpReportListener
import hi3.hashkit.discovery.parseIpReport
import hi3.hashkit.domain.model.MinerIdentity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class SitePhase { SETUP, CAPTURE, DONE }

data class SiteMapState(
    val phase: SitePhase = SitePhase.SETUP,
    val siteName: String = "",
    val buildingsText: String = "1",
    val racksText: String = "",
    val tiersText: String = "",
    val positionsText: String = "",
    val config: SiteMapConfig? = null,
    /** Listening for button presses (start pressed, not paused, not finished). */
    val running: Boolean = false,
    val paused: Boolean = false,
    /** Walk-order index of the slot waiting for its button press. */
    val cursor: Int = 0,
    /** Filled slots keyed by walk-order index. */
    val captured: Map<Int, CapturedSlot> = emptyMap(),
    val skipped: Set<Int> = emptySet(),
    val lastEvent: String? = null,
    val error: String? = null,
    val farms: List<FarmEntity> = emptyList(),
    val saving: Boolean = false,
    val saveResult: String? = null,
)

/**
 * Site Map capture: define the site geometry, then walk the racks pressing each miner's
 * IP Report button while the phone fills the map slot under the cursor. See
 * [hi3.hashkit.discovery.IpReportListener] for the wire protocol and supported models.
 */
@Suppress("TooManyFunctions") // one intent function per user gesture, plus the tiny setup setters
@HiltViewModel
class SiteMapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val listener: IpReportListener,
    private val farmRepository: FarmRepository,
    private val minerRepository: MinerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SiteMapState())
    val state: StateFlow<SiteMapState> = _state.asStateFlow()

    private var listenJob: Job? = null
    private val seenMacs = mutableSetOf<String>()
    private val fillOrder = mutableListOf<Int>()
    private var tone: ToneGenerator? = null

    init {
        viewModelScope.launch {
            farmRepository.observeFarms().collect { farms -> _state.update { it.copy(farms = farms) } }
        }
    }

    fun setSiteName(v: String) = _state.update { it.copy(siteName = v) }
    fun setBuildings(v: String) = _state.update { it.copy(buildingsText = v) }
    fun setRacks(v: String) = _state.update { it.copy(racksText = v) }
    fun setTiers(v: String) = _state.update { it.copy(tiersText = v) }
    fun setPositions(v: String) = _state.update { it.copy(positionsText = v) }

    fun parsedConfig(): SiteMapConfig? {
        val s = _state.value
        val cfg = SiteMapConfig(
            buildings = s.buildingsText.trim().toIntOrNull() ?: 0,
            racksPerBuilding = s.racksText.trim().toIntOrNull() ?: 0,
            tiersPerRack = s.tiersText.trim().toIntOrNull() ?: 0,
            positionsPerTier = s.positionsText.trim().toIntOrNull() ?: 0,
        )
        return cfg.takeIf { it.isValid }
    }

    /** Begin a fresh capture session at B1-R1-T1-P1 and open the UDP listener. */
    fun start() {
        val cfg = parsedConfig() ?: run {
            _state.update { it.copy(error = "Enter a number of buildings, racks, tiers and positions first.") }
            return
        }
        seenMacs.clear()
        fillOrder.clear()
        _state.update {
            it.copy(
                phase = SitePhase.CAPTURE, config = cfg, running = true, paused = false,
                cursor = 0, captured = emptyMap(), skipped = emptySet(),
                lastEvent = null, error = null, saveResult = null,
            )
        }
        openListener()
    }

    /** Pause closes the socket entirely so presses are neither recorded nor acked. */
    fun pause() {
        listenJob?.cancel()
        listenJob = null
        _state.update { it.copy(running = false, paused = true) }
    }

    fun resume() {
        _state.update { it.copy(running = true, paused = false, error = null) }
        openListener()
    }

    /** End the session with whatever has been captured so far. */
    fun stop() = finishCapture()

    fun newSession() {
        listenJob?.cancel()
        listenJob = null
        _state.update { it.copy(phase = SitePhase.SETUP, running = false, paused = false) }
    }

    /** Take back the most recent fill (button or manual) and move the cursor to it. */
    fun undoLast() {
        val last = fillOrder.removeLastOrNull() ?: return
        _state.update { s ->
            val removed = s.captured[last]
            if (removed?.mac != null) seenMacs.remove(removed.mac)
            s.copy(
                phase = SitePhase.CAPTURE, cursor = last, captured = s.captured - last,
                // Undoing out of DONE (or while stopped) leaves the session paused, not live.
                paused = !s.running,
                lastEvent = removed?.let { "Undid ${it.slot.code} (${it.ip})" },
            )
        }
    }

    /** Leave the cursor's slot empty (no miner installed there) and move on. */
    fun skipCurrent() {
        val s = _state.value
        val cfg = s.config ?: return
        if (s.phase != SitePhase.CAPTURE || s.cursor >= cfg.totalSlots) return
        _state.update { it.copy(skipped = it.skipped + it.cursor) }
        advanceFrom(s.cursor)
    }

    /** Fill the cursor's slot by hand — for gear with no IP Report button. */
    fun manualFill(ipText: String) {
        val ip = ipText.trim()
        if (parseIpReport("$ip,00:00:00:00:00:00") == null) {
            _state.update { it.copy(error = "\"$ip\" is not a valid IPv4 address.") }
            return
        }
        record(ip = ip, mac = null, manual = true)
    }

    private fun openListener() {
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            listener.reports()
                .catch { e ->
                    val cause = e.message ?: e.javaClass.simpleName
                    _state.update {
                        it.copy(running = false, paused = true, error = "Listener failed: $cause")
                    }
                }
                .collect { report ->
                    if (_state.value.running && seenMacs.add(report.mac)) {
                        record(ip = report.ip, mac = report.mac, manual = false)
                        feedback()
                    }
                }
        }
    }

    private fun record(ip: String, mac: String?, manual: Boolean) {
        val s = _state.value
        val cfg = s.config ?: return
        if (s.phase != SitePhase.CAPTURE || s.cursor >= cfg.totalSlots) return
        val slot = SiteWalk.slotAt(cfg, s.cursor) ?: return
        val entry = CapturedSlot(slot, ip, mac, manual, System.currentTimeMillis())
        fillOrder += s.cursor
        _state.update {
            it.copy(captured = it.captured + (s.cursor to entry), lastEvent = "${slot.code} ← $ip", error = null)
        }
        advanceFrom(s.cursor)
    }

    private fun advanceFrom(index: Int) {
        val cfg = _state.value.config ?: return
        val next = index + 1
        if (next >= cfg.totalSlots) finishCapture() else _state.update { it.copy(cursor = next) }
    }

    private fun finishCapture() {
        listenJob?.cancel()
        listenJob = null
        _state.update {
            val end = it.config?.totalSlots ?: it.cursor
            it.copy(phase = SitePhase.DONE, running = false, paused = false, cursor = end)
        }
    }

    /** Captured slots in walk order (for the results table, CSV, and farm save). */
    fun capturedInOrder(): List<CapturedSlot> = _state.value.captured.toSortedMap().values.toList()

    /** Write the results CSV into the FileProvider-shared exports dir; caller shares it. */
    fun exportCsv(): File? {
        val rows = capturedInOrder()
        if (rows.isEmpty()) return null
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "hi3-sitemap-$stamp.csv")
        file.writeText(siteMapCsv(rows))
        return file
    }

    /**
     * Register every captured miner (reusing the existing farm when the name matches),
     * assign it to the farm, and write its "B1-R2-T3-P4" code into Location — the field
     * the Rack & site screen groups by.
     */
    fun saveToFarm(farmName: String) {
        val rows = capturedInOrder()
        val name = farmName.trim()
        if (rows.isEmpty() || name.isEmpty()) return
        _state.update { it.copy(saving = true, saveResult = null) }
        viewModelScope.launch {
            val farmId = farmRepository.farms().firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                ?: farmRepository.createFarm(name, subnetsCsvOf(rows))
            var added = 0
            var known = 0
            var failed = 0
            rows.forEach { row ->
                when (upsertRow(row, farmId)) {
                    is AddMinerResult.Added -> added++
                    is AddMinerResult.AlreadyKnown -> known++
                    else -> failed++
                }
            }
            val summary = buildString {
                append("Saved to \"$name\": $added added, $known already known")
                if (failed > 0) append(", $failed failed")
            }
            _state.update { it.copy(saving = false, saveResult = summary) }
        }
    }

    private suspend fun upsertRow(row: CapturedSlot, farmId: Long): AddMinerResult {
        val identity = MinerIdentity(macAddress = row.mac, manufacturer = if (row.mac != null) "Bitmain" else null)
        val result = minerRepository.upsertDiscovered(row.ip, 0, GenericCgMinerAdapter.TYPE, identity)
        val minerId = when (result) {
            is AddMinerResult.Added -> result.minerId
            is AddMinerResult.AlreadyKnown -> result.minerId
            else -> return result
        }
        farmRepository.assignMiner(minerId, farmId)
        val entity = minerRepository.observeMinerEntity(minerId).first() ?: return result
        minerRepository.updateMinerMeta(
            id = minerId,
            name = entity.name,
            group = entity.groupName,
            location = row.slot.code,
            notes = entity.notes,
            tags = entity.tagsCsv.split(',').filter { it.isNotBlank() },
            expectedHashrateGhs = entity.expectedHashrateGhs,
        )
        return result
    }

    private fun subnetsCsvOf(rows: List<CapturedSlot>): String =
        rows.map { it.ip.substringBeforeLast('.') + ".0/24" }.distinct().joinToString(",")

    /** Confirmation beep + buzz so the walker never has to look at the phone mid-row. */
    private fun feedback() {
        runCatching {
            (tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME).also { tone = it })
                .startTone(ToneGenerator.TONE_PROP_ACK, TONE_MS)
        }
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun onCleared() {
        runCatching { tone?.release() }
        tone = null
    }

    private companion object {
        const val TONE_VOLUME = 80
        const val TONE_MS = 120
        const val VIBRATE_MS = 60L
    }
}
