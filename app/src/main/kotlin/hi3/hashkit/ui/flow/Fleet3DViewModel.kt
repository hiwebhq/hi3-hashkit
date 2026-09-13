package hi3.hashkit.ui.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.model.MinerStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** One miner as the 3D scene needs it. */
data class Unit3DUi(
    val id: Long,
    val name: String,
    val host: String,
    val status: MinerStatus,
    val chipTempC: Double?,
    val hashrateGhs: Double?,
    val fanRpm: Int?,
    val locationCode: String?,
    val farmOrdinal: Int,
)

data class Fleet3DState(
    val units: List<Unit3DUi> = emptyList(),
    val rackSize: Int = 4,
)

@HiltViewModel
class Fleet3DViewModel @Inject constructor(
    repository: MinerRepository,
    pollingEngine: PollingEngine,
    private val settingsRepository: SettingsRepository,
    farmRepository: hi3.hashkit.data.repo.FarmRepository,
) : ViewModel() {

    val state: StateFlow<Fleet3DState> =
        combine(
            repository.observeMinerEntities(),
            pollingEngine.lastRefresh,
            settingsRepository.settings,
            farmRepository.observeFarms(),
        ) { entities, _, settings, farms ->
            val farmOrdinals = farms.mapIndexed { i, f -> f.id to i }.toMap()
            val now = Instant.now()
            Fleet3DState(
                units = entities
                    .filter { settings.demoModeEnabled || !it.isDemo }
                    .map { e ->
                        val m = repository.toDomain(e, now)
                        Unit3DUi(
                            id = e.id,
                            name = e.name,
                            host = e.host,
                            status = m.status,
                            chipTempC = m.lastTelemetry?.chipTempC?.value,
                            hashrateGhs = m.lastTelemetry?.hashrateGhs?.value,
                            fanRpm = m.lastTelemetry?.fans?.firstOrNull()?.rpm,
                            locationCode = e.location,
                            farmOrdinal = e.farmId?.let { farmOrdinals[it] } ?: -1,
                        )
                    },
                rackSize = settings.fleet3dRackSize,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Fleet3DState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }

    fun setRackSize(size: Int) {
        viewModelScope.launch { settingsRepository.setFleet3dRackSize(size) }
    }
}
