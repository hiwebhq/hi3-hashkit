package hi3.hashkit.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.R
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

data class PlugConfig(
    val type: hi3.hashkit.integrations.plug.PlugType? = null,
    val host: String = "",
    val onUrl: String = "",
    val offUrl: String = "",
    val cutoffC: Double? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MinerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repository: MinerRepository,
    private val controlRepository: ControlRepository,
    private val registry: AdapterRegistry,
    private val pollingEngine: PollingEngine,
    private val exporter: hi3.hashkit.data.export.Exporter,
    private val smartPlugClient: hi3.hashkit.integrations.plug.SmartPlugClient,
    private val maintenanceDao: hi3.hashkit.data.db.MaintenanceDao,
    private val farmRepository: hi3.hashkit.data.repo.FarmRepository,
    private val personalBestDao: hi3.hashkit.data.db.PersonalBestDao,
    private val statsCardRenderer: hi3.hashkit.ui.share.StatsCardRenderer,
    alertDao: AlertDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Render the shareable stats-card PNG off the main thread and hand back the file. */
    fun shareStatsCard(accentArgb: Int, onReady: (java.io.File) -> Unit) {
        viewModelScope.launch {
            val entity = repository.observeMinerEntity(minerId).first() ?: return@launch
            val t = repository.latestTelemetry(minerId)
            val best = personalBestDao.bestFor(minerId) ?: t?.bestDifficulty
            val netDiff = t?.networkDifficulty?.takeIf { it > 0 }
                ?: settingsRepository.current().networkDifficulty.takeIf { it > 0 }
            val days = ((System.currentTimeMillis() - entity.createdAtEpochMs) / DAY_MS).toInt().coerceAtLeast(0)
            val stats = hi3.hashkit.ui.share.StatsCardRenderer.Stats(
                minerName = entity.name,
                model = entity.model,
                hashrate = hi3.hashkit.core.Units.formatHashrate(t?.hashrateGhs?.value),
                bestShare = hi3.hashkit.core.Units.formatDifficulty(best),
                percentOfBlock = hi3.hashkit.domain.solo.PersonalBests.percentOfBlock(best, netDiff)?.let {
                    appContext.getString(
                        R.string.det_bests_pct_block,
                        hi3.hashkit.domain.solo.PersonalBests.formatPercent(it),
                    )
                },
                uptime = hi3.hashkit.core.Units.formatUptime(t?.uptimeSeconds),
                efficiency = hi3.hashkit.core.Units.formatEfficiency(t?.efficiencyJTh?.value),
                daysMining = days,
                accentArgb = accentArgb,
            )
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                statsCardRenderer.render(stats)
            }
            onReady(file)
        }
    }

    /** Inline rate entry on the cost card (same setting as Settings → Energy). */
    fun setElectricityRate(ratePerKwh: Double) {
        viewModelScope.launch { settingsRepository.setElectricityRate(ratePerKwh) }
    }

    fun setPurchasePrice(price: Double?) {
        viewModelScope.launch { repository.setPurchasePrice(minerId, price) }
    }

    private val minerId: Long = checkNotNull(savedStateHandle["minerId"])

    /** This miner's top personal-best shares, best first. */
    val bests: StateFlow<List<hi3.hashkit.data.db.PersonalBestEntity>> =
        personalBestDao.observeForMiner(minerId, BESTS_SHOWN)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())


    /** User-written maintenance notes for this miner, newest first. */
    val maintenanceNotes: StateFlow<List<hi3.hashkit.data.db.MaintenanceNoteEntity>> =
        maintenanceDao.observeForMiner(minerId)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addMaintenanceNote(text: String, photoUri: android.net.Uri? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && photoUri == null) return
        viewModelScope.launch {
            val photoPath = photoUri?.let {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { copyPhoto(it) }
            }
            maintenanceDao.insert(
                hi3.hashkit.data.db.MaintenanceNoteEntity(
                    minerId = minerId,
                    atEpochMs = System.currentTimeMillis(),
                    text = trimmed,
                    photoPath = photoPath,
                )
            )
        }
    }

    /** Copy a picked image into app-private storage; returns the absolute path or null. */
    private fun copyPhoto(uri: android.net.Uri): String? = runCatching {
        val dir = java.io.File(appContext.filesDir, "maintenance").apply { mkdirs() }
        val file = java.io.File(dir, "note_${System.currentTimeMillis()}.jpg")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        } ?: return null
        file.absolutePath
    }.getOrNull()

    fun deleteMaintenanceNote(note: hi3.hashkit.data.db.MaintenanceNoteEntity) {
        viewModelScope.launch {
            maintenanceDao.delete(note)
            note.photoPath?.let { runCatching { java.io.File(it).delete() } }
        }
    }

    /** Current smart-plug config, surfaced to the detail UI (null type = disabled). */
    val plug: StateFlow<PlugConfig> = repository.observeMinerEntity(minerId)
        .map { e ->
            PlugConfig(
                type = hi3.hashkit.integrations.plug.PlugType.fromName(e?.plugType),
                host = e?.plugHost ?: "",
                onUrl = e?.plugOnUrl ?: "",
                offUrl = e?.plugOffUrl ?: "",
                cutoffC = e?.plugCutoffTempC,
            )
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), PlugConfig())

    /** Whether an admin credential is stored for this miner (for authenticated controls). */
    val credentialSet: StateFlow<Boolean> = repository.observeMinerEntity(minerId)
        .map { !it?.credentialEnc.isNullOrBlank() }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)

    /** Save (or clear, with blank) the miner's admin password / API token, encrypted at rest. */
    fun setCredential(secret: String) {
        viewModelScope.launch {
            repository.setCredential(minerId, secret)
            lastActionMessage.value = if (secret.isBlank()) appContext.getString(R.string.det_msg_credential_cleared)
            else appContext.getString(R.string.det_msg_credential_saved)
        }
    }

    fun saveSmartPlug(type: hi3.hashkit.integrations.plug.PlugType?, host: String, onUrl: String, offUrl: String, cutoffC: Double?) {
        viewModelScope.launch {
            repository.setSmartPlug(minerId, type?.name, host, onUrl, offUrl, cutoffC)
        }
    }

    fun testPlug(turnOn: Boolean) {
        viewModelScope.launch {
            val p = plug.value
            val type = p.type ?: return@launch
            val plugCfg = hi3.hashkit.integrations.plug.SmartPlugClient.Plug(type, p.host, p.onUrl, p.offUrl)
            val ok = if (turnOn) smartPlugClient.turnOn(plugCfg) else smartPlugClient.turnOff(plugCfg)
            lastActionMessage.value = if (ok) {
                if (turnOn) appContext.getString(R.string.det_msg_plug_on_sent)
                else appContext.getString(R.string.det_msg_plug_off_sent)
            } else appContext.getString(R.string.det_msg_plug_failed)
        }
    }
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
        farmId: Long? = FARM_UNCHANGED,
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
            if (farmId != FARM_UNCHANGED) farmRepository.assignMiner(minerId, farmId)
        }
    }

    /** Farms available for the edit dialog's farm picker. */
    val farms: StateFlow<List<hi3.hashkit.data.db.FarmEntity>> =
        farmRepository.observeFarms()
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    /** This miner's current farm id (null = unassigned). */
    val farmId: StateFlow<Long?> = repository.observeMinerEntity(minerId)
        .map { it?.farmId }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)


    /** Build a telemetry CSV for the current window and hand back a share intent. */
    fun exportCsv(onReady: (android.content.Intent) -> Unit) {
        viewModelScope.launch {
            val file = exporter.telemetryCsv(minerId, System.currentTimeMillis() - windowMs.value)
            onReady(exporter.shareIntent(file, "text/csv"))
        }
    }

    fun reboot() = runAction(appContext.getString(R.string.det_act_restarting)) { controlRepository.reboot(it) }

    fun locate(on: Boolean) = runAction(
        if (on) appContext.getString(R.string.det_act_blinking_led)
        else appContext.getString(R.string.det_act_stopping_blink)
    ) {
        controlRepository.locate(it, on)
    }

    fun pauseHashing() = runAction(appContext.getString(R.string.det_act_pausing)) {
        controlRepository.powerControl(it, hi3.hashkit.domain.adapter.PowerAction.PAUSE)
    }

    fun resumeHashing() = runAction(appContext.getString(R.string.det_act_resuming)) {
        controlRepository.powerControl(it, hi3.hashkit.domain.adapter.PowerAction.RESUME)
    }

    fun setPool(url: String, port: Int, worker: String) =
        runAction(appContext.getString(R.string.det_act_changing_pool)) { controlRepository.setPrimaryPool(it, url, port, worker) }

    fun setFanAuto() = runAction(appContext.getString(R.string.det_act_fan_auto)) {
        controlRepository.setFan(it, FanControl.Automatic())
    }

    fun setFanManual(percent: Int) = runAction(appContext.getString(R.string.det_act_fan_manual, percent)) {
        controlRepository.setFan(it, FanControl.Manual(percent))
    }

    fun setDisplay(config: hi3.hashkit.domain.adapter.DisplayControl) =
        runAction(appContext.getString(R.string.det_act_display)) { controlRepository.setDisplay(it, config) }

    fun applyTune(freq: Int, volt: Int) = runAction(appContext.getString(R.string.det_act_applying_tune, freq, volt)) {
        controlRepository.applyTune(it, freq, volt).also { r ->
            if (r is ActionResult.Success) hasRollback.value = true
        }
    }

    fun rollbackTune() = runAction(appContext.getString(R.string.det_act_rollback)) { controlRepository.rollbackTune(it) }

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
                lastActionMessage.value = appContext.getString(R.string.det_msg_miner_not_found)
                return@launch
            }
            busyAction.value = label
            lastActionMessage.value = null
            val result = runCatching { block(entity) }
                .getOrElse { ActionResult.Failure(it.message ?: appContext.getString(R.string.det_msg_unexpected_error)) }
            lastActionMessage.value = when (result) {
                is ActionResult.Success -> appContext.getString(R.string.det_msg_done, label)
                is ActionResult.Failure -> appContext.getString(R.string.det_msg_failed, result.message)
                is ActionResult.Unsupported -> appContext.getString(R.string.det_msg_unsupported, result.reason)
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
        private const val BESTS_SHOWN = 5
        private const val DAY_MS = 86_400_000L

        /** Sentinel: saveMeta callers that don't touch the farm leave the assignment as-is. */
        const val FARM_UNCHANGED = Long.MIN_VALUE
    }
}
