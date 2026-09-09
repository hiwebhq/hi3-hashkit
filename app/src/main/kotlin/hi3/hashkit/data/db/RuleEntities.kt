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
 * An automation rule: when [conditionType] (with optional [threshold]) holds for a target
 * miner, run [actionType] on it. [targetGroup] blank = all real miners. [minIntervalMinutes]
 * throttles how often the rule may fire. Executions are recorded here + in audit_events.
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val enabled: Boolean,
    val label: String,
    /** One of RuleEngine.ConditionType names. */
    val conditionType: String,
    val threshold: Double?,
    /** One of RuleEngine.ActionType names. */
    val actionType: String,
    val targetGroup: String?,
    val minIntervalMinutes: Int,
    val lastFiredAtEpochMs: Long?,
    val lastResult: String?,
)

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY label")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE enabled = 1")
    suspend fun enabled(): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RuleEntity): Long

    @Update
    suspend fun update(rule: RuleEntity)

    @Query("UPDATE rules SET lastFiredAtEpochMs = :at, lastResult = :result WHERE id = :id")
    suspend fun recordRun(id: Long, at: Long, result: String)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long)
}
