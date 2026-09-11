package hi3.hashkit.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import hi3.hashkit.data.poll.MonitorWorker
import hi3.hashkit.data.prefs.AppSettings
import hi3.hashkit.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(val settings: AppSettings = AppSettings())

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SettingsRepository,
    private val exporter: hi3.hashkit.data.export.Exporter,
    private val farmRepository: hi3.hashkit.data.repo.FarmRepository,
    private val appUpdater: hi3.hashkit.integrations.update.AppUpdater,
) : ViewModel() {

    val restoreMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    // ---- Self-update (sideload builds only) --------------------------------------------
    val selfUpdateEnabled: Boolean get() = appUpdater.enabled
    val currentVersion: String get() = appUpdater.currentVersion

    sealed interface UpdateStatus {
        data object Idle : UpdateStatus
        data object Checking : UpdateStatus
        data object UpToDate : UpdateStatus
        data class Available(val info: hi3.hashkit.integrations.update.AppUpdater.UpdateInfo) : UpdateStatus
        data class NeedsPermission(val info: hi3.hashkit.integrations.update.AppUpdater.UpdateInfo) : UpdateStatus
        data class Downloading(val progress: Float) : UpdateStatus
        /** [confirming] = Android is showing its one-tap install dialog (first update only). */
        data class Installing(val confirming: Boolean = false) : UpdateStatus
        data class Error(val message: String) : UpdateStatus
    }

    val updateStatus = kotlinx.coroutines.flow.MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)

    init {
        // Relay install-session outcomes into the settings UI. On success the process is
        // usually replaced before this runs; the branch covers the rare survival case.
        viewModelScope.launch {
            hi3.hashkit.integrations.update.AppUpdater.installEvents.collect { event ->
                when (event) {
                    is hi3.hashkit.integrations.update.AppUpdater.InstallEvent.AwaitingConfirm ->
                        updateStatus.value = UpdateStatus.Installing(confirming = true)
                    is hi3.hashkit.integrations.update.AppUpdater.InstallEvent.Success ->
                        updateStatus.value = UpdateStatus.UpToDate
                    is hi3.hashkit.integrations.update.AppUpdater.InstallEvent.Failed ->
                        updateStatus.value = UpdateStatus.Error(event.message)
                    null -> {}
                }
            }
        }
    }

    fun checkForUpdate() {
        if (!selfUpdateEnabled) return
        updateStatus.value = UpdateStatus.Checking
        viewModelScope.launch {
            val info = appUpdater.check()
            updateStatus.value = when {
                info == null -> UpdateStatus.Error("Couldn't reach the update server.")
                info.isNewer -> UpdateStatus.Available(info)
                else -> UpdateStatus.UpToDate
            }
        }
    }

    /**
     * Download the available update and install it in place. If the user hasn't yet granted
     * "install unknown apps", [onLaunchIntent] receives the system settings screen for that
     * grant instead, and the button can be tapped again on return.
     */
    fun downloadAndInstall(onLaunchIntent: (android.content.Intent) -> Unit) {
        val info = when (val s = updateStatus.value) {
            is UpdateStatus.Available -> s.info
            is UpdateStatus.NeedsPermission -> s.info
            else -> return
        }
        if (!appUpdater.canInstall()) {
            updateStatus.value = UpdateStatus.NeedsPermission(info)
            onLaunchIntent(appUpdater.unknownSourcesIntent())
            return
        }
        updateStatus.value = UpdateStatus.Downloading(0f)
        viewModelScope.launch {
            val file = appUpdater.download(info.apkUrl) { p ->
                updateStatus.value = UpdateStatus.Downloading(p)
            }
            if (file == null) {
                updateStatus.value = UpdateStatus.Error("Download failed.")
            } else {
                updateStatus.value = UpdateStatus.Installing()
                appUpdater.startInstall(file)
            }
        }
    }

    /** Farms, so Settings can show a per-farm refresh-interval control for each. */
    val farms: StateFlow<List<hi3.hashkit.data.db.FarmEntity>> =
        farmRepository.observeFarms()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Global default cadence used when no farm is active (5s..1d). */
    fun setDefaultRefreshIntervalMs(ms: Long) = viewModelScope.launch {
        repo.setPollIntervalMs(ms.coerceIn(5_000L, 86_400_000L))
    }

    fun setFarmRefreshIntervalMs(farmId: Long, ms: Long) = viewModelScope.launch {
        farmRepository.setRefreshInterval(farmId, ms)
    }

    fun setExtraSubnets(csv: String) = viewModelScope.launch { repo.setExtraSubnets(csv) }

    fun exportFleetCsv(onReady: (android.content.Intent) -> Unit) = viewModelScope.launch {
        val file = exporter.telemetryCsv(null, System.currentTimeMillis() - 7 * 86_400_000L)
        onReady(exporter.shareIntent(file, "text/csv"))
    }

    fun exportBackup(passphrase: String?, onReady: (android.content.Intent) -> Unit) = viewModelScope.launch {
        val file = exporter.backupJson(passphrase)
        val mime = if (file.name.endsWith(".hi3enc")) "application/octet-stream" else "application/json"
        onReady(exporter.shareIntent(file, mime))
    }

    /** Set when a restore needs a passphrase; the UI shows a prompt and calls restoreFrom again. */
    val pendingEncryptedRestore = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)

    fun exportDiagnostics(includeAddresses: Boolean, onReady: (android.content.Intent) -> Unit) =
        viewModelScope.launch {
            val file = exporter.diagnostics(includeAddresses)
            onReady(exporter.shareIntent(file, "text/plain"))
        }

    fun setShowSoloCard(v: Boolean) = viewModelScope.launch { repo.setShowSoloCard(v) }
    fun setShowProfitCard(v: Boolean) = viewModelScope.launch { repo.setShowProfitCard(v) }
    fun setThemeMode(v: hi3.hashkit.ui.theme.ThemeMode) = viewModelScope.launch { repo.setThemeMode(v) }
    fun setThemeColor(v: hi3.hashkit.ui.theme.ThemeColor) = viewModelScope.launch { repo.setThemeColor(v) }
    fun setInventoryTagType(v: hi3.hashkit.data.prefs.InventoryTagType) = viewModelScope.launch { repo.setInventoryTagType(v) }
    fun setConfirmBeforeExit(v: Boolean) = viewModelScope.launch { repo.setConfirmBeforeExit(v) }
    fun setAppLockEnabled(v: Boolean) = viewModelScope.launch { repo.setAppLockEnabled(v) }
    fun setMmpEnabled(v: Boolean) = viewModelScope.launch { repo.setMmpEnabled(v) }
    fun setMmpBaseUrl(v: String) = viewModelScope.launch { repo.setMmpBaseUrl(v) }
    fun setMmpApiKey(v: String) = viewModelScope.launch { repo.setMmpApiKey(v) }
    fun setHi3PoolEnabled(v: Boolean) = viewModelScope.launch { repo.setHi3PoolEnabled(v) }
    fun setHi3PoolBaseUrl(v: String) = viewModelScope.launch { repo.setHi3PoolBaseUrl(v) }
    fun setHi3PoolPayoutAddress(v: String) = viewModelScope.launch { repo.setHi3PoolPayoutAddress(v) }
    fun setPoolApiToken(v: String) = viewModelScope.launch { repo.setPoolApiToken(v) }

    /** Switch pools; reset the base URL to the new pool's default so requests hit the right host. */
    fun setPoolType(v: hi3.hashkit.integrations.hi3.PoolType) = viewModelScope.launch {
        repo.setPoolType(v)
        repo.setHi3PoolBaseUrl(v.defaultBaseUrl)
    }

    fun restoreFrom(uri: android.net.Uri, passphrase: String? = null) = viewModelScope.launch {
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        }.getOrNull()
        if (content == null) {
            restoreMessage.value = "Could not read the selected file."
            return@launch
        }
        val result = exporter.restore(content, passphrase)
        if (result == "ENCRYPTED") {
            pendingEncryptedRestore.value = uri // ask the UI for a passphrase
        } else {
            pendingEncryptedRestore.value = null
            restoreMessage.value = result
        }
    }

    val uiState: StateFlow<SettingsUiState> = repo.settings
        .map { SettingsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setBackgroundMonitoring(enabled: Boolean) = viewModelScope.launch {
        repo.setBackgroundMonitoring(enabled)
        if (enabled) MonitorWorker.schedule(context) else MonitorWorker.cancel(context)
    }

    fun setPollIntervalSeconds(seconds: Long) = viewModelScope.launch {
        repo.setPollIntervalMs(seconds.coerceIn(5, 300) * 1000)
    }

    fun setRetentionDays(days: Int) = viewModelScope.launch { repo.setRetentionDays(days) }
    fun setFirmwareUpdateCheck(v: Boolean) = viewModelScope.launch { repo.setFirmwareUpdateCheck(v) }
    fun setAutoRecoverEnabled(v: Boolean) = viewModelScope.launch { repo.setAutoRecoverEnabled(v) }
    fun setAutoRecoverAfterMin(v: Long) = viewModelScope.launch { repo.setAutoRecoverAfterMin(v) }

    /** Toggle the always-on foreground safety monitor (persist + start/stop the service). */
    fun setSafetyService(v: Boolean) = viewModelScope.launch {
        repo.setSafetyServiceEnabled(v)
        if (v) hi3.hashkit.data.poll.SafetyMonitorService.start(context)
        else hi3.hashkit.data.poll.SafetyMonitorService.stop(context)
    }
    fun setAlertsEnabled(v: Boolean) = viewModelScope.launch { repo.setAlertsEnabled(v) }
    fun setHashrateBelowPercent(v: Double) = viewModelScope.launch { repo.setHashrateBelowPercent(v) }
    fun setChipTempThreshold(v: Double) = viewModelScope.launch { repo.setChipTempThreshold(v) }
    fun setVrTempThreshold(v: Double) = viewModelScope.launch { repo.setVrTempThreshold(v) }
    fun setRejectRateThreshold(v: Double) = viewModelScope.launch { repo.setRejectRateThreshold(v) }
    fun setCooldownMinutes(v: Long) = viewModelScope.launch { repo.setCooldownMinutes(v) }
    fun setQuietHoursEnabled(v: Boolean) = viewModelScope.launch { repo.setQuietHoursEnabled(v) }
    fun setQuietStartMinute(v: Int) = viewModelScope.launch { repo.setQuietStartMinute(v) }
    fun setQuietEndMinute(v: Int) = viewModelScope.launch { repo.setQuietEndMinute(v) }
    fun setDigestEnabled(v: Boolean) = viewModelScope.launch { repo.setDigestEnabled(v) }
    fun setDigestHour(v: Int) = viewModelScope.launch { repo.setDigestHour(v) }
    fun setMqttEnabled(v: Boolean) = viewModelScope.launch { repo.setMqttEnabled(v) }
    fun setMqttHost(v: String) = viewModelScope.launch { repo.setMqttHost(v) }
    fun setMqttPort(v: Int) = viewModelScope.launch { repo.setMqttPort(v) }
    fun setMqttUsername(v: String) = viewModelScope.launch { repo.setMqttUsername(v) }
    fun setMqttPassword(v: String) = viewModelScope.launch { repo.setMqttPassword(v) }
    fun setMqttBaseTopic(v: String) = viewModelScope.launch { repo.setMqttBaseTopic(v) }
    fun setMqttHaDiscovery(v: Boolean) = viewModelScope.launch { repo.setMqttHaDiscovery(v) }
    fun setPrometheusEnabled(v: Boolean) = viewModelScope.launch { repo.setPrometheusEnabled(v) }
    fun setPrometheusPort(v: Int) = viewModelScope.launch { repo.setPrometheusPort(v) }
    fun setWebhookType(v: hi3.hashkit.data.alerts.WebhookType) = viewModelScope.launch { repo.setWebhookType(v) }
    fun setWebhookUrl(v: String) = viewModelScope.launch { repo.setWebhookUrl(v) }
    fun setWebhookToken(v: String) = viewModelScope.launch { repo.setWebhookToken(v) }
    fun setWebhookTarget(v: String) = viewModelScope.launch { repo.setWebhookTarget(v) }
    fun setUseFahrenheit(v: Boolean) = viewModelScope.launch { repo.setUseFahrenheit(v) }
    fun setElectricityRate(v: Double) = viewModelScope.launch { repo.setElectricityRate(v) }
    fun setCurrencyCode(v: String) = viewModelScope.launch { repo.setCurrencyCode(v.take(6)) }
    fun setNetworkDifficulty(v: Double) = viewModelScope.launch { repo.setNetworkDifficulty(v) }
    fun setDifficultyAutoFetch(v: Boolean) = viewModelScope.launch { repo.setDifficultyAutoFetch(v) }
    fun setBtcPrice(v: Double) = viewModelScope.launch { repo.setBtcPrice(v) }
    fun setBtcPriceAutoFetch(v: Boolean) = viewModelScope.launch { repo.setBtcPriceAutoFetch(v) }
    fun setDemoMode(v: Boolean) = viewModelScope.launch { repo.setDemoMode(v) }
    fun setAdvancedUnlockCode(v: String) = viewModelScope.launch { repo.setAdvancedUnlockCode(v) }
}
