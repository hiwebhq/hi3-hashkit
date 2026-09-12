package hi3.hashkit.data.repo

import hi3.hashkit.data.db.LogDao
import hi3.hashkit.data.db.LogLineEntity
import hi3.hashkit.domain.logs.LogAnalyzer
import hi3.hashkit.domain.logs.LogClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists captured miner log lines (classified) as a per-miner ring buffer and serves
 * windowed analysis over the stored history. Lines are already wallet/SSID-redacted by the
 * log stream before they reach here.
 */
@Singleton
class LogRepository @Inject constructor(
    private val logDao: LogDao,
) {
    private val lastPrune = AtomicLong(0)

    /** Classify and store a batch of raw log lines for a miner, then enforce retention. */
    suspend fun record(minerId: Long, lines: List<String>) {
        if (lines.isEmpty()) return
        val now = System.currentTimeMillis()
        val rows = lines.mapIndexed { i, text ->
            val c = LogClassifier.classify(text)
            LogLineEntity(
                minerId = minerId,
                // Preserve order within a batch that arrives in the same millisecond.
                atEpochMs = now + i,
                severity = c.severity.name,
                category = c.category.name,
                text = text,
            )
        }
        logDao.insertAll(rows)
        logDao.trimToRecent(minerId, PER_MINER_CAP)
        // Time-based prune at most every 30 min of use.
        if (now - lastPrune.get() > 30 * 60_000L && lastPrune.compareAndSet(lastPrune.get(), now)) {
            logDao.pruneBefore(now - RETENTION_MS)
        }
    }

    suspend fun count(minerId: Long): Int = logDao.count(minerId)

    /** Live view of the newest stored lines, oldest first (for the derived event log). */
    fun observeRecentTexts(minerId: Long, limit: Int): Flow<List<String>> =
        logDao.observeRecent(minerId, limit).map { rows -> rows.asReversed().map { it.text } }

    /** Analyze stored lines from the last [windowMs]; empty window analyzes cleanly. */
    suspend fun analyzeSince(minerId: Long, windowMs: Long): LogAnalyzer.Analysis {
        val since = System.currentTimeMillis() - windowMs
        val texts = logDao.since(minerId, since).map { it.text }
        return LogAnalyzer.analyze(texts)
    }

    /** Stored lines from the last [windowMs] as plain text, newest last (for export). */
    suspend fun textsSince(minerId: Long, windowMs: Long): List<String> =
        logDao.since(minerId, System.currentTimeMillis() - windowMs).map { it.text }

    companion object {
        const val PER_MINER_CAP = 10_000
        const val RETENTION_MS = 7L * 86_400_000L // 7 days
    }
}
