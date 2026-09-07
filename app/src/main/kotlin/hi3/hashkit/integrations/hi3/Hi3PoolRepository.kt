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
    val poolType: PoolType = PoolType.HI3,
    val lastUpdated: Instant? = null,
    val error: String? = null,
    val workersCount: Int = 0,
    val totalPoolHashrateGhs: Double = 0.0,
    val networkDifficulty: Double? = null,
    val blockHeight: Long? = null,
    val comparisons: List<WorkerComparison> = emptyList(),
    /** Local miners the pool doesn't report at all (offline at the pool / other pool). */
    val unmatchedLocal: List<String> = emptyList(),
    /**
     * True when miners reach the pool through a stratum proxy, so the pool aggregates
     * them into far fewer workers than the app tracks locally. The comparison then
     * pits the local fleet total against the pool total rather than per-worker.
     */
    val aggregatedViaProxy: Boolean = false,
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
        val poolType = settings.poolType
        if (!settings.hi3PoolEnabled) {
            _state.value = Hi3PoolState(enabled = false, poolType = poolType)
            return
        }
        // Placeholder pools contact nothing — no address/key is read or sent.
        if (poolType.comingSoon) {
            _state.value = Hi3PoolState(
                enabled = true,
                poolType = poolType,
                error = "${poolType.displayName.removeSuffix(" (coming soon)")} support is coming soon.",
            )
            return
        }
        // Token-based pools (Braiins) need the access token; the rest need the address/subaccount.
        val missingCredential = if (poolType.usesToken) settings.poolApiToken.isBlank()
        else settings.hi3PoolPayoutAddress.isBlank()
        if (missingCredential) {
            val what = if (poolType.usesToken) "access token" else poolType.identifierLabel.lowercase()
            _state.value = Hi3PoolState(
                enabled = true,
                poolType = poolType,
                error = "Set your $what in Settings to load pool stats.",
            )
            return
        }
        val baseUrl = settings.hi3PoolBaseUrl
        val id = settings.hi3PoolPayoutAddress
        val realMiners = localMiners.filter { !it.isDemo }

        // Hi3 has a stratum-proxy endpoint that exposes per-rig sessions by LAN IP; other
        // pools are correlated by worker name from their per-worker account response.
        if (poolType == PoolType.HI3) {
            val account = client.fetchAccount(baseUrl, id)
            val net = (client.fetchNetwork(baseUrl) as? Hi3PoolClient.PoolResult.Ok)?.value
            val sessions = (client.fetchSessions(baseUrl, id)
                as? Hi3PoolClient.PoolResult.Ok)?.value.orEmpty()
            _state.value = when (account) {
                is Hi3PoolClient.PoolResult.Error -> _state.value.copy(enabled = true, poolType = poolType, error = account.message)
                is Hi3PoolClient.PoolResult.Ok ->
                    if (sessions.isNotEmpty()) buildFromSessions(sessions, realMiners, account.value, net)
                    else buildAggregate(account.value, realMiners, net)
            }
            return
        }

        val account = client.fetchAccountFor(poolType, baseUrl, id, settings.poolApiToken)
        // Only public-pool exposes /api/network; skip for the others.
        val net = if (poolType == PoolType.PUBLIC_POOL)
            (client.fetchNetwork(baseUrl) as? Hi3PoolClient.PoolResult.Ok)?.value else null
        _state.value = when (account) {
            is Hi3PoolClient.PoolResult.Error -> _state.value.copy(enabled = true, poolType = poolType, error = account.message)
            is Hi3PoolClient.PoolResult.Ok -> buildFromWorkers(poolType, account.value, realMiners, net)
        }
    }

    /** Per-worker correlation for non-Hi3 pools: match each pool worker to a local miner by name. */
    private fun buildFromWorkers(
        poolType: PoolType,
        account: Hi3PoolClient.PoolAccount,
        miners: List<Miner>,
        net: Hi3PoolClient.PoolNetwork?,
    ): Hi3PoolState {
        val matched = mutableSetOf<String>()
        val comparisons = account.workers.map { w ->
            val local = miners.firstOrNull { m -> matchesWorker(m, w.name) }
            local?.let { matched += it.name }
            WorkerComparison(
                poolWorkerName = local?.name ?: w.name,
                poolHashrateGhs = w.hashRateGhs,
                poolBestDifficulty = w.bestDifficulty,
                localMinerName = local?.name,
                localHashrateGhs = local?.lastTelemetry?.hashrateGhs?.value,
            )
        }.sortedByDescending { it.poolHashrateGhs ?: 0.0 }
        return Hi3PoolState(
            enabled = true,
            poolType = poolType,
            lastUpdated = Instant.now(),
            workersCount = account.workersCount,
            totalPoolHashrateGhs = account.totalHashRateGhs,
            networkDifficulty = net?.difficulty,
            blockHeight = net?.blockHeight,
            comparisons = comparisons,
            unmatchedLocal = miners
                .filter { it.status == MinerStatus.ONLINE && it.name !in matched }
                .map { it.name },
            aggregatedViaProxy = false,
        )
    }

    /** Match a local miner to a pool worker name (rig suffix), by worker suffix, name, or hostname. */
    private fun matchesWorker(miner: Miner, poolWorkerName: String): Boolean {
        val target = poolWorkerName.substringAfterLast('.').trim()
        if (target.isEmpty()) return false
        return localKeyOf(miner)?.equals(target, ignoreCase = true) == true ||
            miner.name.equals(target, ignoreCase = true) ||
            miner.identity.hostname?.equals(target, ignoreCase = true) == true
    }

    private fun buildFromSessions(
        sessions: List<Hi3PoolClient.PoolSession>,
        miners: List<Miner>,
        account: Hi3PoolClient.PoolAccount,
        net: Hi3PoolClient.PoolNetwork?,
    ): Hi3PoolState {
        val comparisons = sessions.map { s ->
            val local = miners.firstOrNull { it.host == s.peerHost }
                ?: miners.firstOrNull { m -> localKeyOf(m)?.let { s.worker.endsWith(it) } == true }
            WorkerComparison(
                poolWorkerName = local?.name ?: s.worker.substringAfterLast('.'),
                poolHashrateGhs = s.hashRateGhs,
                poolBestDifficulty = null,
                localMinerName = local?.name,
                localHashrateGhs = local?.lastTelemetry?.hashrateGhs?.value,
            )
        }.sortedByDescending { it.poolHashrateGhs ?: 0.0 }
        val matchedHosts = sessions.mapNotNull { it.peerHost }.toSet()
        return Hi3PoolState(
            enabled = true,
            lastUpdated = Instant.now(),
            workersCount = sessions.size,
            totalPoolHashrateGhs = sessions.sumOf { it.hashRateGhs ?: 0.0 },
            networkDifficulty = net?.difficulty,
            blockHeight = net?.blockHeight,
            comparisons = comparisons,
            unmatchedLocal = miners
                .filter { it.status == MinerStatus.ONLINE && it.host !in matchedHosts }
                .map { it.name },
            aggregatedViaProxy = false,
        )
    }

    private fun buildAggregate(
        account: Hi3PoolClient.PoolAccount,
        miners: List<Miner>,
        net: Hi3PoolClient.PoolNetwork?,
    ): Hi3PoolState {
        val localTotal = miners
            .filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
            .sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }
        val aggregated = account.workersCount in 1 until miners.size.coerceAtLeast(2)
        return Hi3PoolState(
            enabled = true,
            lastUpdated = Instant.now(),
            workersCount = account.workersCount,
            totalPoolHashrateGhs = account.totalHashRateGhs,
            networkDifficulty = net?.difficulty,
            blockHeight = net?.blockHeight,
            comparisons = listOf(
                WorkerComparison(
                    poolWorkerName = "Fleet (via stratum proxy)",
                    poolHashrateGhs = account.totalHashRateGhs,
                    poolBestDifficulty = null,
                    localMinerName = "${miners.count { it.status == MinerStatus.ONLINE }} miners",
                    localHashrateGhs = localTotal,
                )
            ),
            unmatchedLocal = emptyList(),
            aggregatedViaProxy = aggregated,
        )
    }

    /** Local worker suffix (e.g. "0x51" from "address.0x51"), used as a fallback match. */
    private fun localKeyOf(miner: Miner): String? =
        miner.lastTelemetry?.workerName?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
            ?: miner.identity.hostname
}
