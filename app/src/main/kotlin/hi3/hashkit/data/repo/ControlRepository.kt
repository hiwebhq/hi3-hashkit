package hi3.hashkit.data.repo

import hi3.hashkit.data.db.AuditDao
import hi3.hashkit.data.db.AuditEventEntity
import hi3.hashkit.data.db.MinerEntity
import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.domain.adapter.FanControl
import hi3.hashkit.domain.adapter.MinerControlAdapter
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.domain.adapter.TuneOptions
import hi3.hashkit.domain.adapter.AdapterRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes control actions with a local audit trail: every action records the
 * previous values (for display and rollback) and the applied values. A tune can be
 * rolled back to the audited previous frequency/voltage.
 */
@Singleton
class ControlRepository @Inject constructor(
    private val registry: AdapterRegistry,
    private val auditDao: AuditDao,
    private val minerRepository: MinerRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun controlAdapter(entity: MinerEntity): MinerControlAdapter? =
        registry.byType(entity.adapterType) as? MinerControlAdapter

    /** Miner address with its decrypted admin credential attached (for authenticated controls). */
    private fun hostOf(entity: MinerEntity): MinerHost = MinerHost(
        entity.host,
        entity.port,
        entity.credentialEnc?.let { runCatching { hi3.hashkit.core.KeystoreCrypto.decrypt(it) }.getOrNull() },
    )

    suspend fun tuneOptions(entity: MinerEntity): TuneOptions? =
        controlAdapter(entity)?.getTuneOptions(hostOf(entity))

    suspend fun reboot(entity: MinerEntity): ActionResult {
        val adapter = controlAdapter(entity)
            ?: return ActionResult.Unsupported("No control adapter for ${entity.adapterType}.")
        val result = adapter.reboot(hostOf(entity))
        audit(entity.id, "reboot", "{}", "{}", result)
        return result
    }

    suspend fun powerControl(
        entity: MinerEntity,
        action: hi3.hashkit.domain.adapter.PowerAction,
    ): ActionResult {
        val adapter = controlAdapter(entity)
            ?: return ActionResult.Unsupported("No control adapter for ${entity.adapterType}.")
        val result = adapter.powerControl(hostOf(entity), action)
        audit(entity.id, "power_${action.name.lowercase()}", "{}", "{}", result)
        return result
    }

    suspend fun setPrimaryPool(entity: MinerEntity, url: String, port: Int, worker: String): ActionResult {
        val adapter = controlAdapter(entity)
            ?: return ActionResult.Unsupported("No control adapter for ${entity.adapterType}.")
        val previous = minerRepository.latestTelemetry(entity.id)?.let {
            buildJsonObject {
                put("stratumURL", it.poolUrl ?: "")
                put("stratumPort", it.poolPort ?: 0)
                put("stratumUser", it.workerName ?: "")
            }.toString()
        } ?: "{}"
        val applied = buildJsonObject {
            put("stratumURL", url)
            put("stratumPort", port)
            put("stratumUser", worker)
        }.toString()
        val result = adapter.setPrimaryPool(hostOf(entity), url, port, worker)
        audit(entity.id, ACTION_SET_POOL, previous, applied, result)
        return result
    }

    suspend fun setFan(entity: MinerEntity, config: FanControl): ActionResult {
        val adapter = controlAdapter(entity)
            ?: return ActionResult.Unsupported("No control adapter for ${entity.adapterType}.")
        val applied = when (config) {
            is FanControl.Automatic -> buildJsonObject {
                put("autofanspeed", true)
                config.targetTempC?.let { put("temptarget", it) }
            }
            is FanControl.Manual -> buildJsonObject {
                put("autofanspeed", false)
                put("manualFanSpeed", config.percent)
            }
        }.toString()
        val result = adapter.setFan(hostOf(entity), config)
        audit(entity.id, "set_fan", "{}", applied, result)
        return result
    }

    suspend fun applyTune(entity: MinerEntity, frequencyMhz: Int, coreVoltageMv: Int): ActionResult {
        val adapter = controlAdapter(entity)
            ?: return ActionResult.Unsupported("No control adapter for ${entity.adapterType}.")
        val telemetry = minerRepository.latestTelemetry(entity.id)
        val previous = buildJsonObject {
            put("frequency", telemetry?.frequencyMhz?.value?.toInt() ?: -1)
            put("coreVoltage", telemetry?.coreVoltageMv?.value?.toInt() ?: -1)
        }.toString()
        val applied = buildJsonObject {
            put("frequency", frequencyMhz)
            put("coreVoltage", coreVoltageMv)
        }.toString()
        val result = adapter.applyTune(hostOf(entity), frequencyMhz, coreVoltageMv)
        audit(entity.id, ACTION_TUNE, previous, applied, result)
        return result
    }

    /** Roll a miner back to the frequency/voltage recorded before its last tune. */
    suspend fun rollbackTune(entity: MinerEntity): ActionResult {
        val adapter = controlAdapter(entity)
            ?: return ActionResult.Unsupported("No control adapter for ${entity.adapterType}.")
        val last = auditDao.latestOf(entity.id, ACTION_TUNE)
            ?: return ActionResult.Failure("No recorded tune to roll back.")
        val prev = runCatching { json.parseToJsonElement(last.previousJson).jsonObject }.getOrNull()
            ?: return ActionResult.Failure("Previous tune values are unreadable.")
        val freq = prev["frequency"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
            ?: return ActionResult.Failure("Previous frequency was not recorded.")
        val volt = prev["coreVoltage"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
            ?: return ActionResult.Failure("Previous core voltage was not recorded.")
        val result = adapter.applyTune(hostOf(entity), freq, volt)
        audit(entity.id, "rollback_tune", last.appliedJson, last.previousJson, result)
        return result
    }

    /** The audited previous values of the last tune, if any — for the rollback UI. */
    suspend fun lastTune(minerId: Long): AuditEventEntity? = auditDao.latestOf(minerId, ACTION_TUNE)

    fun observeAudit(minerId: Long) = auditDao.observeForMiner(minerId)

    private suspend fun audit(minerId: Long, action: String, previous: String, applied: String, result: ActionResult) {
        auditDao.insert(
            AuditEventEntity(
                minerId = minerId,
                atEpochMs = System.currentTimeMillis(),
                action = action,
                previousJson = previous,
                appliedJson = applied,
                outcome = when (result) {
                    is ActionResult.Success -> "success"
                    is ActionResult.Failure -> "failure: ${result.message}"
                    is ActionResult.Unsupported -> "unsupported: ${result.reason}"
                },
            )
        )
    }

    companion object {
        const val ACTION_TUNE = "apply_tune"
        const val ACTION_SET_POOL = "set_pool"
    }
}
