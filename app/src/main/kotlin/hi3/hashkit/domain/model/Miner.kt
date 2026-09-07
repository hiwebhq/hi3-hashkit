package hi3.hashkit.domain.model

import java.time.Instant

/** A tracked miner: durable identity + user metadata + last known state. */
data class Miner(
    val id: Long,
    val stableKey: String,
    val adapterType: String,
    val name: String,
    val host: String,
    val port: Int,
    val identity: MinerIdentity,
    val group: String? = null,
    val location: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val expectedHashrateGhs: Double? = null,
    val isDemo: Boolean = false,
    val status: MinerStatus = MinerStatus.UNKNOWN,
    val lastSeenAt: Instant? = null,
    val lastTelemetry: MinerTelemetry? = null,
)
