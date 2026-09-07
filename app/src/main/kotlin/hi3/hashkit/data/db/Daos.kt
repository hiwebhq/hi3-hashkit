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

    @Query(
        "SELECT * FROM telemetry_samples WHERE minerId = :minerId " +
            "AND timestampEpochMs >= :fromEpochMs AND timestampEpochMs < :toEpochMs " +
            "ORDER BY timestampEpochMs"
    )
    suspend fun samplesBetween(minerId: Long, fromEpochMs: Long, toEpochMs: Long): List<TelemetrySampleEntity>

    @Query("SELECT MIN(timestampEpochMs) FROM telemetry_samples WHERE minerId = :minerId")
    suspend fun oldestSampleTimestamp(minerId: Long): Long?

    /**
     * All miners' hashrate samples since [since], for the fleet trend chart. One-shot
     * (recomputed per poll cycle) rather than a Flow, to avoid rebucketing on every
     * per-miner insert.
     */
    @Query(
        "SELECT s.minerId AS minerId, s.timestampEpochMs AS timestampEpochMs, " +
            "s.hashrateGhs AS hashrateGhs FROM telemetry_samples s " +
            "INNER JOIN miners m ON s.minerId = m.id " +
            "WHERE s.timestampEpochMs >= :since AND (m.isDemo = 0 OR :includeDemo = 1) " +
            "ORDER BY s.timestampEpochMs"
    )
    suspend fun fleetSamplesSince(since: Long, includeDemo: Boolean): List<FleetSamplePoint>

    @Insert
    suspend fun insertRaw(raw: RawResponseEntity)

    @Query(
        "SELECT * FROM raw_responses WHERE minerId = :minerId ORDER BY timestampEpochMs DESC LIMIT 1"
    )
    suspend fun latestRaw(minerId: Long): RawResponseEntity?

    @Query("DELETE FROM raw_responses WHERE timestampEpochMs < :beforeEpochMs")
    suspend fun pruneRawBefore(beforeEpochMs: Long)
}
