package hi3.hashkit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Insert
    suspend fun insertEvent(event: AlertEventEntity): Long

    @Query("SELECT * FROM alert_events ORDER BY raisedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<AlertEventEntity>>

    @Query("SELECT * FROM alert_events WHERE minerId = :minerId ORDER BY raisedAtEpochMs DESC LIMIT :limit")
    fun observeForMiner(minerId: Long, limit: Int = 50): Flow<List<AlertEventEntity>>

    @Query("SELECT COUNT(*) FROM alert_events WHERE resolvedAtEpochMs IS NULL AND acknowledged = 0 AND type != 'NEW_BEST_DIFFICULTY'")
    fun observeUnresolvedCount(): Flow<Int>

    @Query(
        "UPDATE alert_events SET resolvedAtEpochMs = :at WHERE minerId = :minerId AND type = :type AND resolvedAtEpochMs IS NULL"
    )
    suspend fun resolveOpen(minerId: Long, type: String, at: Long)

    @Query("UPDATE alert_events SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: Long)

    @Query("UPDATE alert_events SET acknowledged = 1")
    suspend fun acknowledgeAll()

    @Query("SELECT * FROM alert_states WHERE minerId = :minerId")
    suspend fun state(minerId: Long): AlertStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: AlertStateEntity)

    @Query("DELETE FROM alert_events WHERE raisedAtEpochMs < :beforeEpochMs")
    suspend fun pruneBefore(beforeEpochMs: Long)
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(event: AuditEventEntity): Long

    @Query("SELECT * FROM audit_events WHERE minerId = :minerId ORDER BY atEpochMs DESC LIMIT :limit")
    fun observeForMiner(minerId: Long, limit: Int = 50): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_events WHERE minerId = :minerId AND action = :action ORDER BY atEpochMs DESC LIMIT 1")
    suspend fun latestOf(minerId: Long, action: String): AuditEventEntity?
}
