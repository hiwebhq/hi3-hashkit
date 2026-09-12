package hi3.hashkit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A user-written maintenance note for one miner (e.g. "repasted 9/1", "replaced fan 2"),
 * with an optional local photo. Kept entirely on-device; deleted with the miner.
 */
@Entity(
    tableName = "maintenance_notes",
    indices = [Index(value = ["minerId", "atEpochMs"])],
)
data class MaintenanceNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minerId: Long,
    val atEpochMs: Long,
    val text: String,
    /** Absolute path to a copied-in photo in app-private storage; null if none. */
    val photoPath: String? = null,
)

@Dao
interface MaintenanceDao {
    @Query("SELECT * FROM maintenance_notes WHERE minerId = :minerId ORDER BY atEpochMs DESC")
    fun observeForMiner(minerId: Long): Flow<List<MaintenanceNoteEntity>>

    @Query("SELECT * FROM maintenance_notes WHERE minerId = :minerId ORDER BY atEpochMs DESC")
    suspend fun listForMiner(minerId: Long): List<MaintenanceNoteEntity>

    @Insert
    suspend fun insert(note: MaintenanceNoteEntity): Long

    @Delete
    suspend fun delete(note: MaintenanceNoteEntity)

    @Query("DELETE FROM maintenance_notes WHERE minerId = :minerId")
    suspend fun deleteForMiner(minerId: Long)
}
