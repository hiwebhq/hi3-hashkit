package hi3.hashkit.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A farm (site): a named group of miners with its own scan subnet(s). One farm is the
 * default — the one selected on a fresh launch. Miners reference a farm via [MinerEntity.farmId]
 * (null = unassigned). Designed to be reused by future ASIC-discovery and log-audit tooling.
 */
@Entity(tableName = "farms")
data class FarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isDefault: Boolean,
    /** CSV of CIDRs to scan for this farm, e.g. "10.0.0.0/24,192.168.1.0/24". */
    val subnetsCsv: String,
    val notes: String?,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "miners",
    indices = [Index(value = ["stableKey"], unique = true)],
)
data class MinerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stableKey: String,
    val adapterType: String,
    val name: String,
    val host: String,
    val port: Int,
    val macAddress: String?,
    val serialNumber: String?,
    val hostname: String?,
    val manufacturer: String?,
    val model: String?,
    val boardVersion: String?,
    val asicModel: String?,
    val firmwareFamily: String?,
    val firmwareVersion: String?,
    val groupName: String?,
    val location: String?,
    val notes: String?,
    val tagsCsv: String,
    /** Owning farm/site; null = unassigned. */
    @androidx.room.ColumnInfo(defaultValue = "NULL") val farmId: Long? = null,
    val expectedHashrateGhs: Double?,
    val isDemo: Boolean,
    val createdAtEpochMs: Long,
    val lastSeenAtEpochMs: Long?,
    // Per-miner alert overrides (null = use the global threshold).
    @androidx.room.ColumnInfo(defaultValue = "NULL") val alertHashBelowPct: Double? = null,
    @androidx.room.ColumnInfo(defaultValue = "NULL") val alertChipTempC: Double? = null,
    @androidx.room.ColumnInfo(defaultValue = "NULL") val alertVrTempC: Double? = null,
    @androidx.room.ColumnInfo(defaultValue = "NULL") val alertRejectPct: Double? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val alertsMuted: Boolean = false,
)

@Entity(
    tableName = "miner_addresses",
    indices = [Index(value = ["minerId", "host"], unique = true)],
)
data class MinerAddressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minerId: Long,
    val host: String,
    val firstSeenEpochMs: Long,
    val lastSeenEpochMs: Long,
)

@Entity(
    tableName = "telemetry_samples",
    indices = [Index(value = ["minerId", "timestampEpochMs"])],
)
data class TelemetrySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minerId: Long,
    val timestampEpochMs: Long,
    val status: String,
    val hashrateGhs: Double?,
    val hashrateSource: String,
    val expectedHashrateGhs: Double?,
    val powerW: Double?,
    val powerSource: String,
    val efficiencyJTh: Double?,
    val chipTempC: Double?,
    val vrTempC: Double?,
    /** JSON list of {index, rpm, percent}. */
    val fansJson: String,
    val frequencyMhz: Double?,
    val coreVoltageMv: Double?,
    val inputVoltageMv: Double?,
    val asicCount: Int?,
    val sharesAccepted: Long?,
    val sharesRejected: Long?,
    val bestDifficulty: Double?,
    val bestSessionDifficulty: Double?,
    val uptimeSeconds: Long?,
    val networkDifficulty: Double?,
    val poolUrl: String?,
    val poolPort: Int?,
    val workerName: String?,
    val usingFallbackPool: Boolean?,
)

/** Lightweight projection for the fleet trend chart (not a table). */
data class FleetSamplePoint(
    val minerId: Long,
    val timestampEpochMs: Long,
    val hashrateGhs: Double?,
)

/** Raw API bodies kept briefly for diagnostics only (short, configurable retention). */
@Entity(
    tableName = "raw_responses",
    indices = [Index(value = ["minerId", "timestampEpochMs"])],
)
data class RawResponseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minerId: Long,
    val timestampEpochMs: Long,
    val endpoint: String,
    val body: String,
)
