package hi3.hashkit.data.rules

import hi3.hashkit.data.alerts.AlertRepository
import hi3.hashkit.data.db.MinerEntity
import hi3.hashkit.data.db.RuleDao
import hi3.hashkit.data.db.RuleEntity
import hi3.hashkit.data.repo.ControlRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.domain.adapter.PowerAction
import hi3.hashkit.domain.alerts.AlertType
import hi3.hashkit.domain.rules.RuleEngine
import hi3.hashkit.integrations.plug.PlugType
import hi3.hashkit.integrations.plug.SmartPlugClient
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates automation rules each poll cycle and performs their actions. A rule fires at
 * most once per its [RuleEntity.minIntervalMinutes], and only on miners currently matching
 * its condition. Control actions reuse the same verified control path as manual actions;
 * plug actions go through [SmartPlugClient]; "notify" raises an alert.
 */
@Singleton
class RuleRunner @Inject constructor(
    private val ruleDao: RuleDao,
    private val minerRepository: MinerRepository,
    private val controlRepository: ControlRepository,
    private val smartPlugClient: SmartPlugClient,
    private val alertRepository: AlertRepository,
) {
    suspend fun runRules() {
        val rules = ruleDao.enabled()
        if (rules.isEmpty()) return
        val now = System.currentTimeMillis()
        val entities = minerRepository.observeMinerEntities().first().filter { !it.isDemo }

        for (rule in rules) {
            val condition = RuleEngine.ConditionType.fromName(rule.conditionType) ?: continue
            val action = RuleEngine.ActionType.fromName(rule.actionType) ?: continue
            if (rule.lastFiredAtEpochMs != null &&
                now - rule.lastFiredAtEpochMs < rule.minIntervalMinutes * 60_000L
            ) continue

            val targets = entities.filter {
                rule.targetGroup.isNullOrBlank() || it.groupName == rule.targetGroup
            }
            val outcomes = mutableListOf<String>()
            for (entity in targets) {
                val miner = minerRepository.toDomain(entity)
                val telemetry = miner.lastTelemetry ?: continue
                if (!RuleEngine.matches(condition, rule.threshold, telemetry, miner.status, entity.expectedHashrateGhs)) {
                    continue
                }
                outcomes += "${entity.name}: ${performAction(rule, action, entity)}"
            }
            if (outcomes.isNotEmpty()) {
                ruleDao.recordRun(rule.id, now, outcomes.joinToString("; ").take(300))
            }
        }
    }

    private suspend fun performAction(rule: RuleEntity, action: RuleEngine.ActionType, entity: MinerEntity): String =
        when (action) {
            RuleEngine.ActionType.PAUSE -> controlRepository.powerControl(entity, PowerAction.PAUSE).short()
            RuleEngine.ActionType.RESUME -> controlRepository.powerControl(entity, PowerAction.RESUME).short()
            RuleEngine.ActionType.REBOOT -> controlRepository.reboot(entity).short()
            RuleEngine.ActionType.PLUG_OFF -> plugSwitch(entity, on = false)
            RuleEngine.ActionType.PLUG_ON -> plugSwitch(entity, on = true)
            RuleEngine.ActionType.NOTIFY -> {
                alertRepository.raiseEvent(
                    entity.id, entity.name, AlertType.RULE_TRIGGERED,
                    "Rule \"${rule.label}\" matched ${entity.name}.",
                    cooldownMs = rule.minIntervalMinutes * 60_000L,
                )
                "notified"
            }
        }

    private suspend fun plugSwitch(entity: MinerEntity, on: Boolean): String {
        val type = PlugType.fromName(entity.plugType) ?: return "no plug"
        val plug = SmartPlugClient.Plug(type, entity.plugHost, entity.plugOnUrl, entity.plugOffUrl)
        val ok = if (on) smartPlugClient.turnOn(plug) else smartPlugClient.turnOff(plug)
        return if (ok) "plug ${if (on) "on" else "off"}" else "plug failed"
    }

    private fun ActionResult.short(): String = when (this) {
        is ActionResult.Success -> "ok"
        is ActionResult.Failure -> "failed"
        is ActionResult.Unsupported -> "unsupported"
        else -> "done"
    }
}
