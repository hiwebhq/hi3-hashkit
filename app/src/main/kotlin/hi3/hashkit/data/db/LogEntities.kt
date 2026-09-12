package hi3.hashkit.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A captured miner log line (ESP-Miner / AxeOS), classified at capture time. Kept as a
 * per-miner ring buffer so the Log Analyzer can review *past* logs, not just the live
 * stream. Wallet/SSID redaction is applied by the log stream before it reaches here.
 */
@Entity(tableName = "log_lines", indices = [Index(value = ["minerId", "atEpochMs"])])
data class LogLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minerId: Long,
    val atEpochMs: Long,
    val severity: String,
    val category: String,
    val text: String,
)

@Dao
interface LogDao {
    @Insert
    suspend fun insertAll(lines: List<LogLineEntity>)

    @Query("SELECT * FROM log_lines WHERE minerId = :minerId AND atEpochMs >= :sinceEpochMs ORDER BY atEpochMs")
    suspend fun since(minerId: Long, sinceEpochMs: Long): List<LogLineEntity>

    @Query("SELECT * FROM log_lines WHERE minerId = :minerId ORDER BY atEpochMs DESC LIMIT :limit")
    suspend fun recent(minerId: Long, limit: Int): List<LogLineEntity>

    @Query("SELECT * FROM log_lines WHERE minerId = :minerId ORDER BY atEpochMs DESC LIMIT :limit")
    fun observeRecent(minerId: Long, limit: Int): Flow<List<LogLineEntity>>

    @Query("SELECT COUNT(*) FROM log_lines WHERE minerId = :minerId")
    suspend fun count(minerId: Long): Int

    /** Keep only the newest [keep] lines for a miner (per-miner cap). */
    @Query(
        "DELETE FROM log_lines WHERE minerId = :minerId AND id NOT IN " +
            "(SELECT id FROM log_lines WHERE minerId = :minerId ORDER BY atEpochMs DESC LIMIT :keep)"
    )
    suspend fun trimToRecent(minerId: Long, keep: Int)

    @Query("DELETE FROM log_lines WHERE atEpochMs < :beforeEpochMs")
    suspend fun pruneBefore(beforeEpochMs: Long)

    @Query("DELETE FROM log_lines WHERE minerId = :minerId")
    suspend fun deleteForMiner(minerId: Long)
}
