package hi3.hashkit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MinerDao {
    @Query("SELECT * FROM miners ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<MinerEntity>>

    @Query("SELECT * FROM miners WHERE id = :id")
    fun observeById(id: Long): Flow<MinerEntity?>

    @Query("SELECT * FROM miners WHERE id = :id")
    suspend fun byId(id: Long): MinerEntity?

    @Query("SELECT * FROM miners WHERE stableKey = :stableKey")
    suspend fun byStableKey(stableKey: String): MinerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(miner: MinerEntity): Long

    @Update
    suspend fun update(miner: MinerEntity)

    @Query("DELETE FROM miners WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE miners SET host = :host, lastSeenAtEpochMs = :seenAt WHERE id = :id")
    suspend fun updateHostAndSeen(id: Long, host: String, seenAt: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAddress(address: MinerAddressEntity)

    @Query("UPDATE miner_addresses SET lastSeenEpochMs = :seenAt WHERE minerId = :minerId AND host = :host")
    suspend fun touchAddress(minerId: Long, host: String, seenAt: Long)
}

@Dao
interface TelemetryDao {
    @Insert
    suspend fun insert(sample: TelemetrySampleEntity)

    @Query(
        "SELECT * FROM telemetry_samples WHERE minerId = :minerId AND timestampEpochMs >= :sinceEpochMs " +
            "ORDER BY timestampEpochMs"
    )
    fun observeSince(minerId: Long, sinceEpochMs: Long): Flow<List<TelemetrySampleEntity>>

    @Query(
        "SELECT * FROM telemetry_samples WHERE minerId = :minerId ORDER BY timestampEpochMs DESC LIMIT 1"
    )
    suspend fun latest(minerId: Long): TelemetrySampleEntity?

    @Query("DELETE FROM telemetry_samples WHERE timestampEpochMs < :beforeEpochMs")
    suspend fun pruneBefore(beforeEpochMs: Long)

    @Insert
    suspend fun insertRaw(raw: RawResponseEntity)

    @Query(
        "SELECT * FROM raw_responses WHERE minerId = :minerId ORDER BY timestampEpochMs DESC LIMIT 1"
    )
    suspend fun latestRaw(minerId: Long): RawResponseEntity?

    @Query("DELETE FROM raw_responses WHERE timestampEpochMs < :beforeEpochMs")
    suspend fun pruneRawBefore(beforeEpochMs: Long)
}
