package hi3.hashkit.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One personal record: the moment a miner's reported best share difficulty exceeded every
 * value this app had seen for it before. Kept across firmware resets and reboots (the
 * miner's own `bestDiff` is not), so the trophy shelf outlives the device's memory.
 * [atEpochMs] is when the app observed the record, which is at most one poll behind the
 * share itself.
 */
@Entity(
    tableName = "personal_bests",
    indices = [Index(value = ["minerId", "difficulty"])],
)
data class PersonalBestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minerId: Long,
    val difficulty: Double,
    val atEpochMs: Long,
    /** Network difficulty at the time, when known — for "% of a block". */
    val networkDifficulty: Double?,
)

/** Fleet-wide record row with the miner's name joined in (not a table). */
data class PersonalBestRow(
    val id: Long,
    val minerId: Long,
    val minerName: String,
    val difficulty: Double,
    val atEpochMs: Long,
    val networkDifficulty: Double?,
)

@Dao
interface PersonalBestDao {
    @Insert
    suspend fun insert(best: PersonalBestEntity): Long

    @Query("SELECT MAX(difficulty) FROM personal_bests WHERE minerId = :minerId")
    suspend fun bestFor(minerId: Long): Double?

    @Query("SELECT * FROM personal_bests WHERE minerId = :minerId ORDER BY difficulty DESC LIMIT :limit")
    fun observeForMiner(minerId: Long, limit: Int): Flow<List<PersonalBestEntity>>

    @Query(
        "SELECT b.id AS id, b.minerId AS minerId, m.name AS minerName, b.difficulty AS difficulty, " +
            "b.atEpochMs AS atEpochMs, b.networkDifficulty AS networkDifficulty " +
            "FROM personal_bests b INNER JOIN miners m ON m.id = b.minerId " +
            "WHERE m.isDemo = 0 ORDER BY b.difficulty DESC LIMIT :limit"
    )
    fun observeTop(limit: Int): Flow<List<PersonalBestRow>>

    @Query("SELECT * FROM personal_bests WHERE minerId = :minerId ORDER BY atEpochMs")
    suspend fun listForMiner(minerId: Long): List<PersonalBestEntity>

    @Query("DELETE FROM personal_bests WHERE minerId = :minerId")
    suspend fun deleteForMiner(minerId: Long)

    @Query("DELETE FROM personal_bests WHERE minerId NOT IN (SELECT id FROM miners)")
    suspend fun deleteOrphans()
}
