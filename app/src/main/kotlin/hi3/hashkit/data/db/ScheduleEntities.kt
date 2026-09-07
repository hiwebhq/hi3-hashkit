package hi3.hashkit.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A local schedule: at [timeMinutesOfDay] on [daysOfWeekCsv] (MON..SUN), run [actionType]
 * with [paramsJson] against [targetMinerIdsCsv] (empty = all real miners) or [targetGroup].
 * Times are device-local. Executions are recorded in audit_events and summarized here.
 */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val enabled: Boolean,
    val label: String,
    /** One of: set_pool, set_fan, apply_tune, reboot. */
    val actionType: String,
    val paramsJson: String,
    val targetMinerIdsCsv: String,
    val targetGroup: String?,
    val timeMinutesOfDay: Int,
    /** CSV of java.time.DayOfWeek names. */
    val daysOfWeekCsv: String,
    /** Minimum minutes between runs (conflict/min-interval guard). */
    val minIntervalMinutes: Int,
    val lastRunAtEpochMs: Long?,
    val lastResult: String?,
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY timeMinutesOfDay")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE enabled = 1")
    suspend fun enabled(): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Query("UPDATE schedules SET lastRunAtEpochMs = :at, lastResult = :result WHERE id = :id")
    suspend fun recordRun(id: Long, at: Long, result: String)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: Long)
}
