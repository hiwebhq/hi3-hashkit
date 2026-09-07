package hi3.hashkit.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.core.Units
import hi3.hashkit.data.db.AlertDao
import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.data.prefs.AppSettings
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.DifficultyRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.adapter.AdapterRegistry
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerIdentity
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.ValueSource
import hi3.hashkit.domain.solo.SoloMiningMath
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class FleetTotals(
    val totalHashrateGhs: Double,
    val totalMeasuredPowerW: Double,
    val hasEstimatedPower: Boolean,
    val fleetEfficiencyJTh: Double?,
    val online: Int,
    val degraded: Int,
    val offline: Int,
    val unknown: Int,
    val hottestChipC: Double?,
    /** Daily electricity cost estimate; null when no rate configured. */
    val dailyCost: Double?,
    val currencyCode: String,
)

data class SoloSummary(
    val networkDifficulty: Double,
    val bestDifficulty: Double?,
    val pDay: Double,
    val pWeek: Double,
    val pMonth: Double,
    val pYear: Double,
    val expectedSeconds: Double?,
)

data class DashboardUiState(
    val miners: List<Miner> = emptyList(),
    val totals: FleetTotals? = null,
    val solo: SoloSummary? = null,
    val unresolvedAlerts: Int = 0,
    val lastRefresh: Instant? = null,
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: MinerRepository,
    private val pollingEngine: PollingEngine,
    private val settingsRepository: SettingsRepository,
    private val difficultyRepository: DifficultyRepository,
    alertDao: AlertDao,
) : ViewModel() {

    init {
        // Runs only when the user has opted in to the external difficulty fetch.
        viewModelScope.launch { difficultyRepository.refreshIfEnabled() }
    }

    val uiState: StateFlow<DashboardUiState> =
        combine(
            repository.observeMinerEntities(),
            pollingEngine.lastRefresh,
            settingsRepository.settings,
            alertDao.observeUnresolvedCount(),
        ) { entities, refresh, settings, unresolved ->
            val now = Instant.now()
            val miners = entities
                .filter { settings.demoModeEnabled || !it.isDemo }
                .map { repository.toDomain(it, now) }
            val totals = totalsOf(miners, settings)
            DashboardUiState(
                miners = miners,
                totals = totals,
                solo = soloOf(miners, settings),
                unresolvedAlerts = unresolved,
                lastRefresh = refresh,
                settings = settings,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun refreshNow() {
        viewModelScope.launch {
            pollingEngine.pollAllOnce()
            difficultyRepository.refreshIfEnabled()
        }
    }

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDemoMode(enabled)
            if (enabled) ensureDemoMiners()
            pollingEngine.pollAllOnce()
        }
    }

    private suspend fun ensureDemoMiners() {
        repeat(3) { i ->
            repository.upsertDiscovered(
                host = "127.0.0.${i + 1}",
                port = 0,
                adapterType = AdapterRegistry.DEMO_ADAPTER_TYPE,
                identity = MinerIdentity(
                    hostname = "demo-miner-${i + 1}",
                    manufacturer = "Hi3 Demo",
                    model = "Demo Bitaxe (synthetic)",
                    firmwareFamily = "demo",
                ),
                isDemo = true,
            )
        }
    }

    private fun totalsOf(miners: List<Miner>, settings: AppSettings): FleetTotals? {
        if (miners.isEmpty()) return null
        val live = miners.filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
        val hash = live.sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }
        val power = live.sumOf { it.lastTelemetry?.powerW?.value ?: 0.0 }
        return FleetTotals(
            totalHashrateGhs = hash,
            totalMeasuredPowerW = power,
            hasEstimatedPower = live.any { it.lastTelemetry?.powerW?.source == ValueSource.ESTIMATED },
            fleetEfficiencyJTh = Units.efficiencyJTh(power.takeIf { it > 0 }, hash.takeIf { it > 0 }),
            online = miners.count { it.status == MinerStatus.ONLINE },
            degraded = miners.count { it.status == MinerStatus.DEGRADED },
            offline = miners.count { it.status == MinerStatus.OFFLINE },
            unknown = miners.count { it.status == MinerStatus.UNKNOWN },
            hottestChipC = miners.mapNotNull { it.lastTelemetry?.chipTempC?.value }.maxOrNull(),
            dailyCost = settings.electricityRatePerKwh.takeIf { it > 0 && power > 0 }
                ?.let { rate -> power / 1000.0 * 24.0 * rate },
            currencyCode = settings.currencyCode,
        )
    }

    private fun soloOf(miners: List<Miner>, settings: AppSettings): SoloSummary? {
        val difficulty = settings.networkDifficulty
        if (difficulty <= 0) return null
        val real = miners.filter { !it.isDemo }
        val hash = real
            .filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
            .sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }
        if (hash <= 0) return null
        val day = 86_400.0
        return SoloSummary(
            networkDifficulty = difficulty,
            bestDifficulty = real.mapNotNull { it.lastTelemetry?.bestDifficulty }.maxOrNull(),
            pDay = SoloMiningMath.probabilityAtLeastOneBlock(hash, difficulty, day),
            pWeek = SoloMiningMath.probabilityAtLeastOneBlock(hash, difficulty, day * 7),
            pMonth = SoloMiningMath.probabilityAtLeastOneBlock(hash, difficulty, day * 30),
            pYear = SoloMiningMath.probabilityAtLeastOneBlock(hash, difficulty, day * 365.25),
            expectedSeconds = SoloMiningMath.expectedSecondsPerBlock(hash, difficulty),
        )
    }
}
