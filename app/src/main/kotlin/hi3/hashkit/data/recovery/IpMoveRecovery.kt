package hi3.hashkit.data.recovery

import hi3.hashkit.data.db.MinerAddressEntity
import hi3.hashkit.data.db.MinerDao
import hi3.hashkit.data.repo.LogRepository
import hi3.hashkit.discovery.MinerHostValidator
import hi3.hashkit.discovery.SubnetUtils
import hi3.hashkit.domain.adapter.AdapterRegistry
import hi3.hashkit.domain.adapter.MinerHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recovers miners whose IP changed (DHCP renumbering): when a miner with a hardware-based
 * stable key (MAC/serial/fingerprint) has been offline for a while, its /24 is rescanned
 * with the miner's own adapter and, if the same identity answers at a new address, the
 * miner's host is updated in place — with an event-log line instead of a silent mystery.
 *
 * Self-throttled; one subnet per run; miners keyed only by IP can't be matched and are
 * left alone.
 */
@Singleton
class IpMoveRecovery @Inject constructor(
    private val minerDao: MinerDao,
    private val registry: AdapterRegistry,
    private val logRepository: LogRepository,
) {
    private val lastRun = AtomicLong(0)

    suspend fun maybeRecover() {
        val now = System.currentTimeMillis()
        if (now - lastRun.get() < RUN_EVERY_MS) return
        val all = minerDao.observeAll().first().filter { !it.isDemo }
        val movable = all.filter { it.eligibleAt(now) }
        if (movable.isEmpty()) return
        if (!lastRun.compareAndSet(lastRun.get(), now)) return

        // One subnet per run keeps the scan bounded; the next run picks up the next subnet.
        val (cidr, miners) = movable
            .groupBy { SubnetUtils.slash24Of(it.host) }
            .entries.firstOrNull { it.key != null } ?: return
        val knownHosts = all.map { it.host }.toSet()
        val candidates = SubnetUtils.expand(cidr!!)
            ?.filter { it !in knownHosts && MinerHostValidator.isAllowedIp(it) }
        if (!candidates.isNullOrEmpty()) scanSubnet(miners, candidates)
    }

    /** Offline long enough, and identified by hardware (an "ip:"-keyed miner can't be matched). */
    private fun hi3.hashkit.data.db.MinerEntity.eligibleAt(now: Long): Boolean {
        val seen = lastSeenAtEpochMs ?: return false
        return now - seen > OFFLINE_AFTER_MS &&
            (stableKey.startsWith("mac:") || stableKey.startsWith("sn:") || stableKey.startsWith("fp:"))
    }

    private suspend fun scanSubnet(
        miners: List<hi3.hashkit.data.db.MinerEntity>,
        candidates: List<String>,
    ) = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(CONCURRENCY)
        supervisorScope {
            for ((adapterType, lost) in miners.groupBy { it.adapterType }) {
                val adapter = registry.byType(adapterType) ?: continue
                val wanted = lost.associateBy { it.stableKey }
                for (candidate in candidates) {
                    launch {
                        semaphore.withPermit {
                            val identity = runCatching {
                                adapter.getIdentity(MinerHost(candidate, adapter.defaultPort))
                            }.getOrNull() ?: return@withPermit
                            wanted[identity.stableKey(candidate)]?.let { recover(it.id, candidate) }
                        }
                    }
                }
            }
        }
    }

    private suspend fun recover(minerId: Long, newHost: String) {
        val fresh = minerDao.byId(minerId) ?: return
        if (fresh.host == newHost) return
        minerDao.update(fresh.copy(host = newHost))
        minerDao.insertAddress(
            MinerAddressEntity(
                minerId = fresh.id, host = newHost,
                firstSeenEpochMs = System.currentTimeMillis(),
                lastSeenEpochMs = System.currentTimeMillis(),
            )
        )
        logRepository.record(
            fresh.id,
            listOf(
                "W events: miner IP changed ${fresh.host} → $newHost — " +
                    "reconnected automatically (identity match)"
            ),
        )
    }

    companion object {
        const val RUN_EVERY_MS = 15L * 60_000L
        const val OFFLINE_AFTER_MS = 3L * 60_000L
        const val CONCURRENCY = 32
    }
}
