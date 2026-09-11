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
 * A saved pool/wallet entry in the address book: a named stratum URL + port + worker
 * (which for solo pools is the payout address, optionally `.workername`). No pool password
 * is stored — pool changes reuse the firmware's existing password mask, never overwriting it.
 */
@Entity(tableName = "saved_pools")
data class SavedPoolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val url: String,
    val port: Int,
    val worker: String,
    /** Whether the Advanced pool speed test includes this pool. */
    val includeInTest: Boolean = true,
)

@Dao
interface SavedPoolDao {
    @Query("SELECT * FROM saved_pools ORDER BY label")
    fun observeAll(): Flow<List<SavedPoolEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pool: SavedPoolEntity): Long

    @Update
    suspend fun update(pool: SavedPoolEntity)

    @Query("DELETE FROM saved_pools WHERE id = :id")
    suspend fun delete(id: Long)
}
