package hi3.hashkit.integrations.hi3

import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** One worker matched (or not) between the local fleet and the pool's view. */
data class WorkerComparison(
    val poolWorkerName: String,
    val poolHashrateGhs: Double?,
    val poolBestDifficulty: Double?,
    /** Local miner matched by worker-name suffix, if any. */
    val localMinerName: String?,
    val localHashrateGhs: Double?,
) {
    /** Pool-side vs miner-side delta in percent, negative = pool sees less. */
    val deltaPercent: Double?
        get() {
            val local = localHashrateGhs ?: return null
            val pool = poolHashrateGhs ?: return null
            if (local <= 0) return null
            return (pool - local) / local * 100.0
        }
}

data class Hi3PoolState(
    val enabled: Boolean = false,
    val lastUpdated: Instant? = null,
    val error: String? = null,
    val workersCount: Int = 0,
    val totalPoolHashrateGhs: Double = 0.0,
    val networkDifficulty: Double? = null,
    val blockHeight: Long? = null,
    val comparisons: List<WorkerComparison> = emptyList(),
    /** Local miners the pool doesn't report at all (offline at the pool / other pool). */
    val unmatchedLocal: List<String> = emptyList(),
)

/**
 * Opt-in pool-side stats. refresh() is a no-op unless the user has enabled the
 * integration and configured a payout address; nothing is contacted otherwise.
 */
@Singleton
class Hi3PoolRepository @Inject constructor(
    private val client: Hi3PoolClient,
    private val settingsRepository: SettingsRepository,
) {
    private val _state = MutableStateFlow(Hi3PoolState())
    val state: StateFlow<Hi3PoolState> = _state

    suspend fun refresh(localMiners: List<Miner>) {
        val settings = settingsRepository.current()
        if (!settings.hi3PoolEnabled) {
            _state.value = Hi3PoolState(enabled = false)
            return
        }
        if (settings.hi3PoolPayoutAddress.isBlank()) {
            _state.value = Hi3PoolState(
                enabled = true,
                error = "Set your payout address in Settings to load pool stats.",
            )
            return
        }

        val account = client.fetchAccount(settings.hi3PoolBaseUrl, settings.hi3PoolPayoutAddress)
        val network = client.fetchNetwork(settings.hi3PoolBaseUrl)

        when (account) {
            is Hi3PoolClient.PoolResult.Error ->
                _state.value = _state.value.copy(enabled = true, error = account.message)
            is Hi3PoolClient.PoolResult.Ok -> {
                val net = (network as? Hi3PoolClient.PoolResult.Ok)?.value
                _state.value = Hi3PoolState(
                    enabled = true,
                    lastUpdated = Instant.now(),
                    error = null,
                    workersCount = account.value.workersCount,
                    totalPoolHashrateGhs = account.value.totalHashRateGhs,
                    networkDifficulty = net?.difficulty,
                    blockHeight = net?.blockHeight,
                    comparisons = compare(account.value.workers, localMiners),
                    unmatchedLocal = unmatchedLocal(account.value.workers, localMiners),
                )
            }
        }
    }

    /**
     * Match pool workers to local miners. Local workerName is "address.worker"; the
     * pool reports just the worker suffix. Falls back to hostname/name matching.
     */
    private fun localKeyOf(miner: Miner): String? =
        miner.lastTelemetry?.workerName?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
            ?: miner.identity.hostname

    private fun compare(
        poolWorkers: List<Hi3PoolClient.PoolWorker>,
        localMiners: List<Miner>,
    ): List<WorkerComparison> {
        val locals = localMiners.filter { !it.isDemo }
        return poolWorkers.map { pw ->
            val match = locals.firstOrNull { m ->
                val key = localKeyOf(m)
                key != null && (key.equals(pw.name, true) || m.name.equals(pw.name, true))
            }
            WorkerComparison(
                poolWorkerName = pw.name,
                poolHashrateGhs = pw.hashRateGhs,
                poolBestDifficulty = pw.bestDifficulty,
                localMinerName = match?.name,
                localHashrateGhs = match?.lastTelemetry?.hashrateGhs?.value,
            )
        }.sortedByDescending { it.poolHashrateGhs ?: 0.0 }
    }

    private fun unmatchedLocal(
        poolWorkers: List<Hi3PoolClient.PoolWorker>,
        localMiners: List<Miner>,
    ): List<String> =
        localMiners
            .filter { !it.isDemo && it.status == MinerStatus.ONLINE }
            .filter { m ->
                val key = localKeyOf(m)
                poolWorkers.none { pw ->
                    (key != null && key.equals(pw.name, true)) || m.name.equals(pw.name, true)
                }
            }
            .map { it.name }
}
