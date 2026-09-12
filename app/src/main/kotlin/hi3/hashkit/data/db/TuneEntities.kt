package hi3.hashkit.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One measured auto-tune operating point, persisted so the tuning optimizer can build an
 * efficiency curve across sweeps over time (not just the current in-memory sweep). Grouped by
 * [sweepStartEpochMs] so an individual sweep can be identified.
 */
@Entity(tableName = "tune_sweeps", indices = [Index(value = ["minerId", "atEpochMs"])])
data class TuneSweepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minerId: Long,
    val sweepStartEpochMs: Long,
    val atEpochMs: Long,
    val frequencyMhz: Int,
    val voltageMv: Int,
    val hashrateGhs: Double?,
    val powerW: Double?,
    val efficiencyJTh: Double?,
    val chipTempC: Double?,
    val overTemp: Boolean,
)

@Dao
interface TuneSweepDao {
    @Insert
    suspend fun insertAll(points: List<TuneSweepEntity>)

    @Query("SELECT * FROM tune_sweeps WHERE minerId = :minerId ORDER BY atEpochMs")
    fun observeForMiner(minerId: Long): Flow<List<TuneSweepEntity>>

    @Query("DELETE FROM tune_sweeps WHERE minerId = :minerId")
    suspend fun deleteForMiner(minerId: Long)
}
