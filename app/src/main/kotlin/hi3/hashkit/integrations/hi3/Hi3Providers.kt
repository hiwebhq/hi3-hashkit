package hi3.hashkit.integrations.hi3

/**
 * Hi3 platform integrations.
 *
 * Hi3 POOL: implemented ([Hi3PoolClient]/[Hi3PoolRepository]) — opt-in, read-only,
 * address-keyed queries against the pool's verified public-pool-fork API.
 *
 * Hi3 MMP: the read-only fleet view is implemented ([MmpClient]/[MmpRepository]) —
 * opt-in, Bearer-key auth, key stored via Android Keystore. The AGENT-side surface
 * below (registering this device as a site agent, uploading telemetry, receiving
 * remote commands) remains a PLACEHOLDER with no implementation — the phone never
 * uploads miner data. If ever built, it must be opt-in with a full data disclosure.
 */

data class RegistrationResult(val success: Boolean, val agentId: String?)
data class SyncResult(val success: Boolean)
data class UploadResult(val success: Boolean, val accepted: Int)
data class TelemetryBatch(val minerStableKeys: List<String>)
data class RemoteCommand(val id: String, val type: String)

interface Hi3MmpProvider {
    suspend fun registerAgent(): RegistrationResult
    suspend fun synchronizeMinerIdentity(minerStableKey: String): SyncResult
    suspend fun uploadTelemetry(batch: TelemetryBatch): UploadResult
    suspend fun receiveAuthorizedCommand(): RemoteCommand?
}
