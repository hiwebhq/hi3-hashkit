package hi3.hashkit.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.data.db.AlertEventEntity
import hi3.hashkit.data.db.AlertDao
import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.data.prefs.AppSettings
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.ControlRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.domain.adapter.AdapterRegistry
import hi3.hashkit.domain.adapter.FanControl
import hi3.hashkit.domain.adapter.TuneOptions
import hi3.hashkit.domain.health.HealthScore
import hi3.hashkit.domain.health.HealthScoreCalculator
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerCapabilities
import hi3.hashkit.domain.model.MinerTelemetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class MinerDetailUiState(
    val miner: Miner? = null,
    val history: List<MinerTelemetry> = emptyList(),
    val healthScore: HealthScore? = null,
    val capabilities: MinerCapabilities? = null,
    val tuneOptions: TuneOptions? = null,
    val hasTuneToRollback: Boolean = false,
    val alerts: List<AlertEventEntity> = emptyList(),
    val rawResponse: String? = null,
    val showRaw: Boolean = false,
    val busyAction: String? = null,
    val lastActionMessage: String? = null,
    val settings: AppSettings = AppSettings(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MinerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MinerRepository,
    private val controlRepository: ControlRepository,
    private val registry: AdapterRegistry,
    private val pollingEngine: PollingEngine,
    private val exporter: hi3.hashkit.data.export.Exporter,
    alertDao: AlertDao,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val minerId: Long = checkNotNull(savedStateHandle["minerId"])
    private val windowMs = MutableStateFlow(HISTORY_WINDOW_MS)
    private val showRaw = MutableStateFlow(false)
    private val busyAction = MutableStateFlow<String?>(null)
    private val lastActionMessage = MutableStateFlow<String?>(null)
    private val tuneOptions = MutableStateFlow<TuneOptions?>(null)
    private val hasRollback = MutableStateFlow(false)

    val uiState: StateFlow<MinerDetailUiState> = combine(
        combine(
            repository.observeMinerEntity(minerId),
            windowMs.flatMapLatest { w ->
                // Raw where available, hourly aggregates beyond raw retention.
                repository.observeHistoryMerged(minerId, System.currentTimeMillis() - w)
            },
            pollingEngine.lastRefresh,
            settingsRepository.settings,
            alertDao.observeForMiner(minerId, 20),
        ) { entity, history, _, settings, alerts ->
            val miner = entity?.let { repository.toDomain(it, Instant.now()) }
            val previousUptime = history.dropLast(1).lastOrNull()?.uptimeSeconds
            MinerDetailUiState(
                miner = miner,
                history = history,
                healthScore = HealthScoreCalculator.calculate(
                    telemetry = miner?.lastTelemetry,
                    expectedHashrateGhs = miner?.expectedHashrateGhs,
                    previousUptimeS = previousUptime,
                ),
                capabilities = miner?.let {
                    registry.byType(it.adapterType)?.getCapabilities(it.identity)
                },
                alerts = alerts,
                settings = settings,
            )
        },
        showRaw,
        busyAction,
        lastActionMessage,
        combine(tuneOptions, hasRollback) { t, r -> t to r },
    ) { base, raw, busy, message, tuneAndRollback ->
        base.copy(
            showRaw = raw,
            rawResponse = if (raw) redact(repository.latestRawResponse(minerId)) else null,
            busyAction = busy,
            lastActionMessage = message,
            tuneOptions = tuneAndRollback.first,
            hasTuneToRollback = tuneAndRollback.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MinerDetailUiState())

    init {
        viewModelScope.launch {
            val entity = repository.observeMinerEntity(minerId).first() ?: return@launch
            tuneOptions.value = runCatching { controlRepository.tuneOptions(entity) }.getOrNull()
            hasRollback.value = controlRepository.lastTune(minerId) != null
        }
    }

    fun toggleRaw() {
        showRaw.value = !showRaw.value
    }

    fun setWindow(ms: Long) {
        windowMs.value = ms
    }

    val currentWindowMs: Long get() = windowMs.value

    fun saveMeta(
        name: String,
        group: String?,
        location: String?,
        notes: String?,
        tagsCsv: String,
        expectedHashrateGhs: Double?,
        alertOverrides: hi3.hashkit.domain.alerts.AlertOverrides,
    ) {
        viewModelScope.launch {
            repository.updateMinerMeta(
                id = minerId,
                name = name,
                group = group,
                location = location,
                notes = notes,
                tags = tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                expectedHashrateGhs = expectedHashrateGhs,
                alertOverrides = alertOverrides,
            )
        }
    }

    /** Build a telemetry CSV for the current window and hand back a share intent. */
    fun exportCsv(onReady: (android.content.Intent) -> Unit) {
        viewModelScope.launch {
            val file = exporter.telemetryCsv(minerId, System.currentTimeMillis() - windowMs.value)
            onReady(exporter.shareIntent(file, "text/csv"))
        }
    }

    fun reboot() = runAction("restarting") { controlRepository.reboot(it) }

    fun pauseHashing() = runAction("pausing hashing") {
        controlRepository.powerControl(it, hi3.hashkit.domain.adapter.PowerAction.PAUSE)
    }

    fun resumeHashing() = runAction("resuming hashing") {
        controlRepository.powerControl(it, hi3.hashkit.domain.adapter.PowerAction.RESUME)
    }

    fun setPool(url: String, port: Int, worker: String) =
        runAction("changing pool") { controlRepository.setPrimaryPool(it, url, port, worker) }

    fun setFanAuto() = runAction("setting fan to automatic") {
        controlRepository.setFan(it, FanControl.Automatic())
    }

    fun setFanManual(percent: Int) = runAction("setting fan to $percent%") {
        controlRepository.setFan(it, FanControl.Manual(percent))
    }

    fun applyTune(freq: Int, volt: Int) = runAction("applying $freq MHz / $volt mV") {
        controlRepository.applyTune(it, freq, volt).also { r ->
            if (r is ActionResult.Success) hasRollback.value = true
        }
    }

    fun rollbackTune() = runAction("rolling back tune") { controlRepository.rollbackTune(it) }

    fun deleteMiner(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteMiner(minerId)
            onDeleted()
        }
    }

    private fun runAction(
        label: String,
        block: suspend (hi3.hashkit.data.db.MinerEntity) -> ActionResult,
    ) {
        if (busyAction.value != null) return
        viewModelScope.launch {
            val entity = repository.observeMinerEntity(minerId).first()
            if (entity == null) {
                lastActionMessage.value = "Miner not found."
                return@launch
            }
            busyAction.value = label
            lastActionMessage.value = null
            val result = runCatching { block(entity) }
                .getOrElse { ActionResult.Failure(it.message ?: "Unexpected error") }
            lastActionMessage.value = when (result) {
                is ActionResult.Success -> "Done: $label."
                is ActionResult.Failure -> "Failed: ${result.message}"
                is ActionResult.Unsupported -> "Unsupported: ${result.reason}"
            }
            busyAction.value = null
            // Post-change monitoring: poll promptly so the effect (or trouble) is visible.
            if (result is ActionResult.Success) {
                delay(2_000)
                runCatching { repository.pollMiner(entity) }
                delay(8_000)
                runCatching { repository.pollMiner(entity) }
            }
        }
    }

    /** Redact credential-bearing fields before showing raw firmware responses. */
    private fun redact(body: String?): String? {
        if (body == null) return null
        return body.replace(
            Regex("\"([^\"]*(?:User|user|pass|Pass|ssid|SSID)[^\"]*)\"\\s*:\\s*\"[^\"]*\""),
            "\"$1\": \"[redacted]\"",
        )
    }

    companion object {
        const val HISTORY_WINDOW_MS = 3_600_000L
    }
}
