package hi3.hashkit.data.remediation

import hi3.hashkit.data.db.AuditDao
import hi3.hashkit.data.db.AuditEventEntity
import hi3.hashkit.data.db.MinerEntity
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.ControlRepository
import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.integrations.plug.PlugType
import hi3.hashkit.integrations.plug.SmartPlugClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in self-healing: when a miner stays offline past the configured threshold, try to
 * recover it once — power-cycle a configured smart plug (off → wait → on), or, failing
 * that, reboot it through its verified control path. Every attempt is audit-logged, and a
 * cooldown prevents recovery loops. Nothing happens unless the user enables it.
 */
@Singleton
class RemediationEngine @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val controlRepository: ControlRepository,
    private val smartPlugClient: SmartPlugClient,
    private val auditDao: AuditDao,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val offlineSinceMs = ConcurrentHashMap<Long, Long>()
    private val lastAttemptMs = ConcurrentHashMap<Long, Long>()

    /** Called once per poll per miner with its freshly-observed status. */
    fun onPolled(entity: MinerEntity, status: MinerStatus) {
        if (entity.isDemo) return
        val now = System.currentTimeMillis()
        if (status != MinerStatus.OFFLINE) {
            // Recovered (or never down): reset tracking so a future outage starts fresh.
            offlineSinceMs.remove(entity.id)
            lastAttemptMs.remove(entity.id)
            return
        }
        val since = offlineSinceMs.getOrPut(entity.id) { now }
        scope.launch {
            val settings = settingsRepository.current()
            if (!settings.autoRecoverEnabled) return@launch
            val thresholdMs = settings.autoRecoverAfterMin * 60_000L
            if (now - since < thresholdMs) return@launch
            // Cooldown: don't retry within one threshold window of the last attempt.
            val lastAttempt = lastAttemptMs[entity.id]
            if (lastAttempt != null && now - lastAttempt < thresholdMs) return@launch
            lastAttemptMs[entity.id] = now
            recover(entity)
        }
    }

    private suspend fun recover(entity: MinerEntity) {
        val plugType = PlugType.fromName(entity.plugType)
        val (method, result) = if (plugType != null) {
            "power_cycle" to powerCycle(plugType, entity)
        } else {
            "reboot" to controlRepository.reboot(entity)
        }
        runCatching {
            auditDao.insert(
                AuditEventEntity(
                    minerId = entity.id,
                    atEpochMs = System.currentTimeMillis(),
                    action = "auto_recover",
                    previousJson = "{\"status\":\"offline\"}",
                    appliedJson = "{\"method\":\"$method\"}",
                    outcome = when (result) {
                        is ActionResult.Success -> "recovery sent ($method)"
                        is ActionResult.Failure -> "failed: ${result.message}"
                        is ActionResult.Unsupported -> "unsupported: ${result.reason}"
                    },
                )
            )
        }
    }

    /** Off → 20s → on. Only turning the plug back on brings the miner up. */
    private suspend fun powerCycle(type: PlugType, entity: MinerEntity): ActionResult {
        val plug = SmartPlugClient.Plug(type, entity.plugHost, entity.plugOnUrl, entity.plugOffUrl)
        if (!smartPlugClient.turnOff(plug)) return ActionResult.Failure("plug off failed")
        delay(20_000)
        return if (smartPlugClient.turnOn(plug)) ActionResult.Success else ActionResult.Failure("plug on failed")
    }
}
