package hi3.hashkit.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val expectedHashrateGhs: Double?,
    val isDemo: Boolean,
    val createdAtEpochMs: Long,
    val lastSeenAtEpochMs: Long?,
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
    val poolUrl: String?,
    val poolPort: Int?,
    val workerName: String?,
    val usingFallbackPool: Boolean?,
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
