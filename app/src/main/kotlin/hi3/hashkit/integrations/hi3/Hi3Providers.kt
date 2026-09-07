package hi3.hashkit.integrations.hi3

/**
 * PLACEHOLDER INTERFACES — DISABLED IN VERSION 1.
 *
 * Future opt-in integrations with pool.hi3.cc and mmp.hi3.cc. Version 1 must never
 * contact either domain, and there are intentionally no implementations, no HTTP code,
 * and no references from production UI beyond a disabled "coming soon" settings row.
 * When implemented, each integration must clearly display exactly what data will be
 * transmitted before the user enables it.
 */

data class AuthenticationResult(val success: Boolean, val message: String?)
data class PoolWorker(val name: String, val hashrateGhs: Double?)
data class PoolHashrate(val currentGhs: Double?, val dayAvgGhs: Double?)
data class ShareStatistics(val accepted: Long, val rejected: Long, val stale: Long)

interface Hi3PoolProvider {
    suspend fun authenticate(): AuthenticationResult
    suspend fun getWorkers(): List<PoolWorker>
    suspend fun getAccountHashrate(): PoolHashrate
    suspend fun getShareStatistics(): ShareStatistics
}

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
