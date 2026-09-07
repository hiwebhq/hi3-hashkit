package hi3.hashkit.integrations.hi3

/**
 * Hi3 platform integrations.
 *
 * Hi3 POOL is now implemented (see [Hi3PoolClient] / Hi3PoolRepository): opt-in,
 * read-only, address-keyed queries against the pool's verified public-pool-fork API.
 *
 * Hi3 MMP below remains a PLACEHOLDER: no implementation, no HTTP code, never
 * contacted. When implemented it must be opt-in and clearly display exactly what
 * data will be transmitted before the user enables it.
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
