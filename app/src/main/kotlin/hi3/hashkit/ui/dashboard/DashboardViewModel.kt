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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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

data class BulkFlowState(
    val plan: hi3.hashkit.data.repo.BulkPlan? = null,
    val outcomes: List<hi3.hashkit.data.repo.BulkOutcome>? = null,
    val running: Boolean = false,
)

data class DashboardUiState(
    val miners: List<Miner> = emptyList(),
    /** Miners grouped by group name (preserving overall order); single-group fleets get one section. */
    val groups: List<Pair<String?, List<Miner>>> = emptyList(),
    val totals: FleetTotals? = null,
    val solo: SoloSummary? = null,
    val unresolvedAlerts: Int = 0,
    val lastRefresh: Instant? = null,
    val settings: AppSettings = AppSettings(),
    val searchQuery: String = "",
    val selection: Set<Long> = emptySet(),
    val bulk: BulkFlowState = BulkFlowState(),
    /** Farms/sites, for the dashboard farm switcher (empty until any are created). */
    val farms: List<hi3.hashkit.data.db.FarmEntity> = emptyList(),
    /** Currently viewed farm; -1 = All farms. */
    val activeFarmId: Long = -1,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: MinerRepository,
    private val pollingEngine: PollingEngine,
    private val settingsRepository: SettingsRepository,
    private val difficultyRepository: DifficultyRepository,
    private val fleetControl: hi3.hashkit.data.repo.FleetControl,
    private val hi3PoolRepository: hi3.hashkit.integrations.hi3.Hi3PoolRepository,
    private val mmpRepository: hi3.hashkit.integrations.hi3.MmpRepository,
    private val scanner: hi3.hashkit.discovery.MinerScanner,
    private val networkInspector: hi3.hashkit.discovery.NetworkInspector,
    private val farmRepository: hi3.hashkit.data.repo.FarmRepository,
    alertDao: AlertDao,
) : ViewModel() {

    val poolState = hi3PoolRepository.state
    val mmpState = mmpRepository.state

    private val fleetWindow = kotlinx.coroutines.flow.MutableStateFlow(6 * 3_600_000L)
    val fleetWindowMs: StateFlow<Long> = fleetWindow

    fun setFleetWindow(ms: Long) { fleetWindow.value = ms }

    /** Per-miner ~30min hashrate sparkline points, recomputed each poll cycle. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sparklines: StateFlow<Map<Long, List<Double>>> =
        combine(
            pollingEngine.lastRefresh,
            settingsRepository.settings.map { it.demoModeEnabled }.distinctUntilChanged(),
        ) { _, demo -> demo }
            .mapLatest { demo -> repository.minerSparklines(30 * 60_000L, demo) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Fleet-total hashrate trend, recomputed each poll cycle (or when window/demo change). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val fleetTrend: StateFlow<List<hi3.hashkit.data.repo.FleetTrendPoint>> =
        combine(
            pollingEngine.lastRefresh,
            fleetWindow,
            settingsRepository.settings.map { it.demoModeEnabled }.distinctUntilChanged(),
        ) { _, window, demo -> window to demo }
            .mapLatest { (window, demo) -> repository.fleetHashrateTrend(window, demo) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val searchQuery = kotlinx.coroutines.flow.MutableStateFlow("")
    private val selection = kotlinx.coroutines.flow.MutableStateFlow<Set<Long>>(emptySet())
    private val bulkFlow = kotlinx.coroutines.flow.MutableStateFlow(BulkFlowState())

    init {
        // Both run only when the user has opted in; otherwise they no-op locally.
        viewModelScope.launch { difficultyRepository.refreshIfEnabled() }
        viewModelScope.launch {
            while (true) {
                refreshPool()
                kotlinx.coroutines.delay(60_000)
            }
        }
    }

    private suspend fun refreshPool() {
        runCatching {
            val miners = repository.observeMinerEntities().first()
                .map { repository.toDomain(it, Instant.now()) }
            hi3PoolRepository.refresh(miners)
        }
        runCatching { mmpRepository.refresh() }
    }

    val uiState: StateFlow<DashboardUiState> =
        combine(
            combine(
                repository.observeMinerEntities(),
                pollingEngine.lastRefresh,
                settingsRepository.settings,
                alertDao.observeUnresolvedCount(),
                farmRepository.observeFarms(),
            ) { entities, refresh, settings, unresolved, farms ->
                val now = Instant.now()
                // When a farm is active, show only its miners (demo miners always show in demo mode).
                val activeFarm = settings.activeFarmId
                val miners = entities
                    .filter { e ->
                        val demoOk = settings.demoModeEnabled || !e.isDemo
                        val farmOk = activeFarm <= 0 || e.farmId == activeFarm || e.isDemo
                        demoOk && farmOk
                    }
                    .map { repository.toDomain(it, now) }
                DashboardUiState(
                    miners = miners,
                    totals = totalsOf(miners, settings),
                    solo = soloOf(miners, settings),
                    unresolvedAlerts = unresolved,
                    lastRefresh = refresh,
                    settings = settings,
                    farms = farms,
                    activeFarmId = activeFarm,
                )
            },
            searchQuery,
            selection,
            bulkFlow,
        ) { base, query, selected, bulk ->
            val filtered = if (query.isBlank()) base.miners else base.miners.filter {
                it.name.contains(query, true) ||
                    (it.identity.model ?: "").contains(query, true) ||
                    it.host.contains(query, true) ||
                    (it.group ?: "").contains(query, true) ||
                    it.tags.any { tag -> tag.contains(query, true) }
            }
            base.copy(
                miners = filtered,
                groups = filtered.groupBy { it.group }.toList()
                    .sortedBy { it.first ?: "￿" },
                searchQuery = query,
                selection = selected.filter { id -> filtered.any { it.id == id } }.toSet(),
                bulk = bulk,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    // ------------------------------------------------------------- search/select ----

    fun setSearch(query: String) {
        searchQuery.value = query
    }

    /** Switch the dashboard to a farm (-1 = All farms). */
    fun setActiveFarm(id: Long) {
        viewModelScope.launch { farmRepository.setActive(id) }
    }

    fun setDensity(density: hi3.hashkit.data.prefs.CardDensity) {
        viewModelScope.launch { settingsRepository.setCardDensity(density) }
    }

    fun toggleSelect(id: Long) {
        selection.value = selection.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        selection.value = emptySet()
        bulkFlow.value = BulkFlowState()
    }

    // ---------------------------------------------------------------- bulk flow ----

    fun planBulk(action: hi3.hashkit.data.repo.BulkAction) {
        viewModelScope.launch {
            val entities = repository.observeMinerEntities().first()
                .filter { it.id in selection.value }
            bulkFlow.value = BulkFlowState(plan = fleetControl.plan(action, entities))
        }
    }

    fun executeBulk() {
        val plan = bulkFlow.value.plan ?: return
        if (bulkFlow.value.running) return
        viewModelScope.launch {
            bulkFlow.value = bulkFlow.value.copy(running = true)
            val outcomes = fleetControl.execute(plan)
            bulkFlow.value = BulkFlowState(plan = plan, outcomes = outcomes, running = false)
            pollingEngine.pollAllOnce()
        }
    }

    fun dismissBulk() {
        bulkFlow.value = BulkFlowState()
    }

    private val _refreshing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    fun refreshNow() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                pollingEngine.pollAllOnce()
                difficultyRepository.refreshIfEnabled()
                refreshPool()
            } finally {
                _refreshing.value = false
            }
        }
    }

    private val _rescanMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val rescanMessage: StateFlow<String?> = _rescanMessage

    /** Re-scan the current Wi-Fi/Ethernet /24 for new miners, from the dashboard. */
    fun rescanLocalNetwork() {
        viewModelScope.launch {
            val cidr = networkInspector.defaultScanCidr()
            if (cidr == null) {
                _rescanMessage.value = "No local network detected to scan."
                return@launch
            }
            val hosts = hi3.hashkit.discovery.SubnetUtils.expand(cidr)
            if (hosts == null) {
                _rescanMessage.value = "Local range too large to auto-scan; use Add → Scan."
                return@launch
            }
            _rescanMessage.value = "Scanning ${hosts.size} hosts…"
            var found = 0
            scanner.scan(hosts).collect { event ->
                when (event) {
                    is hi3.hashkit.discovery.ScanEvent.Found -> {
                        repository.upsertDiscovered(
                            host = event.host, port = 0,
                            adapterType = event.probe.adapterType, identity = event.probe.identity,
                        )
                        found++
                    }
                    is hi3.hashkit.discovery.ScanEvent.Finished -> {
                        _rescanMessage.value = "Rescan done — $found miner(s) found."
                        pollingEngine.pollAllOnce()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun clearRescanMessage() { _rescanMessage.value = null }

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
        // Prefer difficulty reported by a miner itself (Canaan `coin`); fall back to
        // the manually entered / opt-in-fetched value in settings.
        val minerReported = miners
            .filter { !it.isDemo }
            .mapNotNull { it.lastTelemetry?.networkDifficulty }
            .maxOrNull()
        val difficulty = minerReported ?: settings.networkDifficulty
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
