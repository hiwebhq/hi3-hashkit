package hi3.hashkit.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One miner-hour of downsampled telemetry (~24 rows/day/miner vs ~5.8k raw samples at
 * 15 s polling). Raw samples are pruned per the retention setting; these summaries are
 * kept long-term so charts and stats reach back cheaply.
 */
@Entity(
    tableName = "telemetry_hourly",
    primaryKeys = ["minerId", "hourStartEpochMs"],
)
data class TelemetryHourlyEntity(
    val minerId: Long,
    val hourStartEpochMs: Long,
    val samples: Int,
    val onlineSamples: Int,
    val avgHashrateGhs: Double?,
    val minHashrateGhs: Double?,
    val maxHashrateGhs: Double?,
    val avgPowerW: Double?,
    val avgChipTempC: Double?,
    val maxChipTempC: Double?,
    val maxVrTempC: Double?,
    /** Energy integrated across the hour's samples, Wh. */
    val energyWh: Double?,
)

@Dao
interface HourlyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<TelemetryHourlyEntity>)

    @Query(
        "SELECT * FROM telemetry_hourly WHERE minerId = :minerId AND hourStartEpochMs >= :sinceEpochMs " +
            "ORDER BY hourStartEpochMs"
    )
    fun observeSince(minerId: Long, sinceEpochMs: Long): Flow<List<TelemetryHourlyEntity>>

    @Query(
        "SELECT * FROM telemetry_hourly WHERE minerId = :minerId AND hourStartEpochMs >= :sinceEpochMs " +
            "ORDER BY hourStartEpochMs"
    )
    suspend fun listSince(minerId: Long, sinceEpochMs: Long): List<TelemetryHourlyEntity>

    @Query("SELECT MAX(hourStartEpochMs) FROM telemetry_hourly WHERE minerId = :minerId")
    suspend fun highWaterMark(minerId: Long): Long?

    @Query("DELETE FROM telemetry_hourly WHERE hourStartEpochMs < :beforeEpochMs")
    suspend fun pruneBefore(beforeEpochMs: Long)
}
