package hi3.hashkit.ui.farms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.data.db.FarmEntity
import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.FarmRepository
import hi3.hashkit.discovery.AutoScanManager
import hi3.hashkit.discovery.NetworkInspector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FarmRow(
    val farm: FarmEntity,
    val minerCount: Int,
)

@HiltViewModel
class FarmsViewModel @Inject constructor(
    private val farmRepository: FarmRepository,
    private val autoScan: AutoScanManager,
    private val settingsRepository: SettingsRepository,
    private val networkInspector: NetworkInspector,
    private val minerRepository: hi3.hashkit.data.repo.MinerRepository,
    pollingEngine: PollingEngine,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<FarmRow>> =
        combine(farmRepository.observeFarms(), pollingEngine.lastRefresh) { farms, _ -> farms }
            .mapLatest { farms -> farms.map { FarmRow(it, farmRepository.countInFarm(it.id)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeFarmId: StateFlow<Long> =
        settingsRepository.settings
            .map { it.activeFarmId }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1L)

    /** A sensible subnet prefill for the add dialog: this device's detected /24. */
    fun detectedCidr(): String =
        networkInspector.defaultScanCidr()?.let { "${it.baseIp}/${it.prefix}" } ?: ""

    /** Create a farm and return its id so the UI can offer an immediate scan. */
    fun createFarm(name: String, subnet: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = farmRepository.createFarm(name, subnet)
            onCreated(id)
        }
    }

    fun setDefault(id: Long) { viewModelScope.launch { farmRepository.setDefault(id) } }
    fun setActive(id: Long) { viewModelScope.launch { farmRepository.setActive(id) } }
    /**
     * Delete a farm; with [alsoMiners] its miners (and their history) are deleted too,
     * via the full per-miner cascade (maintenance photos included) — otherwise they
     * just become unassigned as before.
     */
    fun delete(id: Long, alsoMiners: Boolean = false) {
        viewModelScope.launch {
            if (alsoMiners) {
                minerRepository.observeMinerEntities().first()
                    .filter { it.farmId == id }
                    .forEach { minerRepository.deleteMiner(it.id) }
            }
            farmRepository.deleteFarm(id)
        }
    }
    fun scanFarm(id: Long) = autoScan.scanFarm(id)

    val scanState: StateFlow<AutoScanManager.State> = autoScan.state
}
