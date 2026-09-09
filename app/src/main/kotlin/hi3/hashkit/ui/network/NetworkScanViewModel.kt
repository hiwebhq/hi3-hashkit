package hi3.hashkit.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.discovery.AutoScanManager
import hi3.hashkit.discovery.NsdDiscoverer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Thin control surface over [AutoScanManager] plus the persisted auto-scan toggle. */
@HiltViewModel
class NetworkScanViewModel @Inject constructor(
    private val autoScan: AutoScanManager,
    private val settingsRepository: SettingsRepository,
    private val nsdDiscoverer: NsdDiscoverer,
) : ViewModel() {

    val state: StateFlow<AutoScanManager.State> = autoScan.state

    /** True while an mDNS discovery pass is running (before the host scan starts). */
    val mdnsRunning = MutableStateFlow(false)

    /** Browse mDNS for miner web UIs, then probe whatever private addresses turn up. */
    fun discoverMdns() {
        if (mdnsRunning.value || state.value.running) return
        viewModelScope.launch {
            mdnsRunning.value = true
            val hosts = runCatching { nsdDiscoverer.discover() }.getOrDefault(emptyList())
            mdnsRunning.value = false
            autoScan.startWithHosts(hosts, sourceLabel = "mDNS")
        }
    }

    val autoScanOnStartup: StateFlow<Boolean> =
        settingsRepository.settings
            .map { it.autoScanOnStartup }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        autoScan.refreshNetwork()
    }

    fun onCidrChange(value: String) = autoScan.setCidr(value)
    fun start() = autoScan.start()
    fun stop() = autoScan.stop()
    fun restart() = autoScan.restart()
    fun refreshNetwork() = autoScan.refreshNetwork()

    fun setAutoScanOnStartup(value: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoScanOnStartup(value) }
    }
}
