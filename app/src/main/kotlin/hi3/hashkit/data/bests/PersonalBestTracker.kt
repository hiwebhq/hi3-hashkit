package hi3.hashkit.data.bests

import hi3.hashkit.data.db.PersonalBestDao
import hi3.hashkit.data.db.PersonalBestEntity
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.solo.PersonalBests
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records a personal best whenever a miner's reported best share difficulty exceeds
 * everything this app has recorded for it. Called once per poll; the known best is
 * cached per miner so the steady state costs no database reads. Notification of the
 * moment itself is the alert evaluator's NEW_BEST_DIFFICULTY job — this only keeps the
 * record book.
 */
@Singleton
class PersonalBestTracker @Inject constructor(
    private val dao: PersonalBestDao,
) {
    private val knownBest = ConcurrentHashMap<Long, Double>()

    suspend fun onPolled(minerId: Long, telemetry: MinerTelemetry) {
        val candidate = telemetry.bestDifficulty?.takeIf { it > 0 } ?: return
        val known = knownBest[minerId] ?: (dao.bestFor(minerId) ?: 0.0).also { knownBest[minerId] = it }
        if (!PersonalBests.isNewRecord(candidate, known)) return
        dao.insert(
            PersonalBestEntity(
                minerId = minerId,
                difficulty = candidate,
                atEpochMs = System.currentTimeMillis(),
                networkDifficulty = telemetry.networkDifficulty?.takeIf { it > 0 },
            )
        )
        knownBest[minerId] = candidate
    }

    /** Drop the cached best (after a miner is deleted or its records restored). */
    fun forget(minerId: Long) {
        knownBest.remove(minerId)
    }
}
