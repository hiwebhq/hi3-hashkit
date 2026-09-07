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
) : ViewModel() {

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
    fun setAlertsEnabled(v: Boolean) = viewModelScope.launch { repo.setAlertsEnabled(v) }
    fun setHashrateBelowPercent(v: Double) = viewModelScope.launch { repo.setHashrateBelowPercent(v) }
    fun setChipTempThreshold(v: Double) = viewModelScope.launch { repo.setChipTempThreshold(v) }
    fun setVrTempThreshold(v: Double) = viewModelScope.launch { repo.setVrTempThreshold(v) }
    fun setRejectRateThreshold(v: Double) = viewModelScope.launch { repo.setRejectRateThreshold(v) }
    fun setCooldownMinutes(v: Long) = viewModelScope.launch { repo.setCooldownMinutes(v) }
    fun setUseFahrenheit(v: Boolean) = viewModelScope.launch { repo.setUseFahrenheit(v) }
    fun setElectricityRate(v: Double) = viewModelScope.launch { repo.setElectricityRate(v) }
    fun setCurrencyCode(v: String) = viewModelScope.launch { repo.setCurrencyCode(v.take(6)) }
    fun setNetworkDifficulty(v: Double) = viewModelScope.launch { repo.setNetworkDifficulty(v) }
    fun setDifficultyAutoFetch(v: Boolean) = viewModelScope.launch { repo.setDifficultyAutoFetch(v) }
    fun setDemoMode(v: Boolean) = viewModelScope.launch { repo.setDemoMode(v) }
}
