package hi3.hashkit.ui.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.discovery.ConnectivityProbe
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import javax.inject.Inject

data class MinerNode(
    val id: Long,
    val name: String,
    val hashrateGhs: Double?,
    val powerW: Double?,
    val powerEstimatedOrMissing: Boolean,
    val status: MinerStatus,
    val healthFraction: Float, // 0..1 for glow intensity, from attainment
    val stratumKey: String,    // which stratum node this miner feeds
)

data class StratumNode(
    val host: String,
    val port: Int,
    val label: String,
    val minerCount: Int,
    val anyFallback: Boolean,
    val latencyMs: Long? = null,
    val reachable: Boolean = true,
)

data class FlowUiState(
    val internetUp: Boolean = true,
    val networkDifficulty: Double? = null,
    val blockHeight: Long? = null,
    val blockTimeEpochSec: Long? = null,
    /** Miner tile columns: 0 = classic single row, else 2..8 (NxN grid). */
    val gridCols: Int = 0,
    val stratums: List<StratumNode> = emptyList(),
    val miners: List<MinerNode> = emptyList(),
    val totalHashrateGhs: Double = 0.0,
    val totalPowerW: Double = 0.0,
    val anyEstimatedPower: Boolean = false,
    val onlineCount: Int = 0,
    val minerCount: Int = 0,
    val lastProbe: Instant? = null,
)

@HiltViewModel
class FlowViewModel @Inject constructor(
    private val repository: MinerRepository,
    private val connectivity: ConnectivityProbe,
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val probeResults = kotlinx.coroutines.flow.MutableStateFlow(ProbeSnapshot())

    /** Last successfully fetched tip height + timestamp, kept across transient fetch failures. */
    private var lastBlockHeight: Long? = null
    private var lastBlockTimeEpochSec: Long? = null

    private data class ProbeSnapshot(
        val internetUp: Boolean = true,
        val latencyByStratum: Map<String, Long?> = emptyMap(),
        val blockHeight: Long? = null,
        val blockTimeEpochSec: Long? = null,
        val at: Instant? = null,
    )

    val uiState: StateFlow<FlowUiState> = combine(
        repository.observeMinerEntities(),
        probeResults,
        settingsRepository.settings,
    ) { entities, probe, settings ->
        val now = Instant.now()
        val miners = entities
            .filter { settings.demoModeEnabled || !it.isDemo }
            .map { repository.toDomain(it, now) }
        buildState(miners, probe).copy(gridCols = settings.flowGridCols)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FlowUiState())

    fun setGridCols(cols: Int) {
        viewModelScope.launch { settingsRepository.setFlowGridCols(cols) }
    }

    /** Called by the screen's lifecycle: probe uplink + stratum latency every 10s while open. */
    fun startProbing() {
        viewModelScope.launch {
            while (true) {
                runCatching { probeOnce() }
                delay(10_000)
            }
        }
    }

    private suspend fun probeOnce() {
        val miners = repository.observeMinerEntities().first()
            .map { repository.toDomain(it, Instant.now()) }
            .filter { !it.isDemo }
        val stratums = distinctStratums(miners)
        val latencies = stratums.associate { s ->
            stratumKey(s.host, s.port) to connectivity.tcpLatencyMs(s.host, s.port)
        }
        val up = connectivity.internetValidated()
        // Current tip block from mempool.space (public, keyless) — only while the uplink is up;
        // keep the last known values across transient failures.
        if (up) fetchTipBlock()?.let { (height, timeSec) ->
            lastBlockHeight = height
            timeSec?.let { lastBlockTimeEpochSec = it }
        }
        probeResults.value = ProbeSnapshot(
            internetUp = up,
            latencyByStratum = latencies,
            blockHeight = lastBlockHeight,
            blockTimeEpochSec = lastBlockTimeEpochSec,
            at = Instant.now(),
        )
    }

    /** GET mempool.space/api/v1/blocks — newest-first block list; tip height + found time. */
    private suspend fun fetchTipBlock(): Pair<Long, Long?>? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://mempool.space/api/v1/blocks").get().build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val tip = kotlinx.serialization.json.Json.parseToJsonElement(body)
                    .let { it as? kotlinx.serialization.json.JsonArray }
                    ?.firstOrNull()?.let { it as? kotlinx.serialization.json.JsonObject }
                    ?: return@use null
                val height = tip["height"]?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                    ?.content?.toLongOrNull() ?: return@use null
                val timeSec = tip["timestamp"]?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                    ?.content?.toLongOrNull()
                height to timeSec
            }
        }.getOrNull()
    }

    private fun stratumKey(host: String, port: Int) = "$host:$port"

    private fun distinctStratums(miners: List<Miner>): List<StratumNode> {
        val byKey = miners
            .mapNotNull { m ->
                val host = m.lastTelemetry?.poolUrl ?: return@mapNotNull null
                val port = m.lastTelemetry?.poolPort ?: 3333
                Triple(host, port, m)
            }
            .groupBy { stratumKey(it.first, it.second) }
        return byKey.map { (_, rows) ->
            val host = rows.first().first
            val port = rows.first().second
            StratumNode(
                host = host,
                port = port,
                label = host,
                minerCount = rows.size,
                anyFallback = rows.any { it.third.lastTelemetry?.usingFallbackPool == true },
            )
        }
    }

    private fun buildState(miners: List<Miner>, probe: ProbeSnapshot): FlowUiState {
        val live = miners.filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
        val stratums = distinctStratums(miners).map { s ->
            val lat = probe.latencyByStratum[stratumKey(s.host, s.port)]
            s.copy(latencyMs = lat, reachable = probe.latencyByStratum.containsKey(stratumKey(s.host, s.port)).not() || lat != null)
        }
        val minerNodes = miners.map { m ->
            val t = m.lastTelemetry
            val attain = t?.attainmentPercent
            MinerNode(
                id = m.id,
                name = m.name,
                hashrateGhs = t?.hashrateGhs?.value,
                powerW = t?.powerW?.value,
                powerEstimatedOrMissing = t?.powerW?.value == null ||
                    t.powerW.source == hi3.hashkit.domain.model.ValueSource.ESTIMATED,
                status = m.status,
                healthFraction = ((attain ?: 100.0) / 100.0).coerceIn(0.0, 1.0).toFloat(),
                stratumKey = t?.poolUrl?.let { stratumKey(it, t.poolPort ?: 3333) } ?: "",
            )
        }
        return FlowUiState(
            internetUp = probe.internetUp,
            networkDifficulty = miners.mapNotNull { it.lastTelemetry?.networkDifficulty }.maxOrNull(),
            blockHeight = probe.blockHeight,
            blockTimeEpochSec = probe.blockTimeEpochSec,
            stratums = stratums,
            miners = minerNodes,
            totalHashrateGhs = live.sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 },
            totalPowerW = live.sumOf { it.lastTelemetry?.powerW?.value ?: 0.0 },
            anyEstimatedPower = minerNodes.any { it.powerEstimatedOrMissing },
            onlineCount = miners.count { it.status == MinerStatus.ONLINE },
            minerCount = miners.size,
            lastProbe = probe.at,
        )
    }
}
