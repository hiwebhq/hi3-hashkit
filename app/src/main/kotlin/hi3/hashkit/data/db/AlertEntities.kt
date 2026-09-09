package hi3.hashkit.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alert_events",
    indices = [Index(value = ["minerId", "raisedAtEpochMs"]), Index(value = ["acknowledged"])],
)
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minerId: Long,
    val minerName: String,
    val type: String,
    val message: String,
    val raisedAtEpochMs: Long,
    /** Set when the recovery condition fired. */
    val resolvedAtEpochMs: Long?,
    val acknowledged: Boolean,
)

/** Per-miner evaluator state so dedup and transitions survive app restarts. */
@Entity(tableName = "alert_states")
data class AlertStateEntity(
    @PrimaryKey val minerId: Long,
    val consecutiveFailures: Int = 0,
    val wasOffline: Boolean = false,
    val previousUptimeS: Long? = null,
    val previousPoolUrl: String? = null,
    val previousBestDifficulty: Double? = null,
    /** CSV of active AlertType names. */
    val activeTypesCsv: String = "",
    /** JSON map of AlertType -> last-notified epoch ms, for cooldowns. */
    val lastNotifiedJson: String = "{}",
)

/** Local audit trail of every control action the app performs. */
@Entity(
    tableName = "audit_events",
    indices = [Index(value = ["minerId", "atEpochMs"])],
)
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minerId: Long,
    val atEpochMs: Long,
    val action: String,
    /** JSON of values before the change, for display and rollback. */
    val previousJson: String,
    /** JSON of values applied. */
    val appliedJson: String,
    val outcome: String,
)
