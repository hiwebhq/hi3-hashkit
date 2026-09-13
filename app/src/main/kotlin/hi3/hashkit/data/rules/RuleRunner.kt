package hi3.hashkit.data.rules

import hi3.hashkit.data.alerts.AlertRepository
import hi3.hashkit.data.db.MinerEntity
import hi3.hashkit.data.db.RuleDao
import hi3.hashkit.data.db.RuleEntity
import hi3.hashkit.data.repo.BulkAction
import hi3.hashkit.data.repo.FleetControl
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.domain.adapter.PowerAction
import hi3.hashkit.domain.alerts.AlertType
import hi3.hashkit.domain.rules.RuleEngine
import hi3.hashkit.integrations.plug.PlugType
import hi3.hashkit.integrations.plug.SmartPlugClient
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates automation rules each poll cycle and performs their actions — the watchdog.
 * A rule's condition must hold continuously for [RuleEntity.sustainedForMinutes] before it
 * fires (0 = first match), and each rule fires at most once per [RuleEntity.minIntervalMinutes]
 * *per miner*, so one flapping miner never suppresses protection of the rest. Muted miners are
 * skipped entirely. Control actions are capability-checked through [FleetControl] (same
 * plan/execute path as manual bulk actions); plug actions go through [SmartPlugClient];
 * every fired action also raises a watchdog alert so it is never silent.
 *
 * Sustain/cooldown state is in-memory: after a process restart the sustained window starts
 * over, which errs on the side of not acting — the same trade-off RemediationEngine makes.
 */
@Singleton
class RuleRunner @Inject constructor(
    private val ruleDao: RuleDao,
    private val minerRepository: MinerRepository,
    private val fleetControl: FleetControl,
    private val smartPlugClient: SmartPlugClient,
    private val alertRepository: AlertRepository,
) {
    /** "ruleId:minerId" -> epoch ms the condition was first seen true (cleared on non-match). */
    private val conditionSince = ConcurrentHashMap<String, Long>()

    /** "ruleId:minerId" -> epoch ms the rule last fired for that miner. */
    private val lastFiredAt = ConcurrentHashMap<String, Long>()

    suspend fun runRules() {
        val rules = ruleDao.enabled()
        if (rules.isEmpty()) return
        val now = System.currentTimeMillis()
        val entities = minerRepository.observeMinerEntities().first().filter { !it.isDemo }

        for (rule in rules) {
            val condition = RuleEngine.ConditionType.fromName(rule.conditionType) ?: continue
            val action = RuleEngine.ActionType.fromName(rule.actionType) ?: continue
            val targets = entities.filter {
                rule.targetGroup.isNullOrBlank() || it.groupName == rule.targetGroup
            }
            val outcomes = mutableListOf<String>()
            for (entity in targets) {
                if (entity.alertsMuted) continue
                val key = "${rule.id}:${entity.id}"
                val miner = minerRepository.toDomain(entity)
                val telemetry = miner.lastTelemetry
                val matches = telemetry != null && RuleEngine.matches(
                    condition, rule.threshold, telemetry, miner.status, entity.expectedHashrateGhs,
                )
                val decision = RuleEngine.sustain(
                    matchesNow = matches,
                    sinceEpochMs = conditionSince[key],
                    nowEpochMs = now,
                    sustainedForMinutes = rule.sustainedForMinutes,
                )
                if (decision.sinceEpochMs != null) {
                    conditionSince[key] = decision.sinceEpochMs
                } else {
                    conditionSince.remove(key)
                }
                if (!decision.fire) continue
                val cooledUntil = (lastFiredAt[key] ?: 0L) + rule.minIntervalMinutes * 60_000L
                if (now < cooledUntil) continue

                lastFiredAt[key] = now
                val outcome = performAction(action, entity)
                outcomes += "${entity.name}: $outcome"
                announce(rule, action, entity, outcome)
            }
            if (outcomes.isNotEmpty()) {
                ruleDao.recordRun(rule.id, now, outcomes.joinToString("; ").take(300))
            }
        }
    }

    private suspend fun performAction(action: RuleEngine.ActionType, entity: MinerEntity): String =
        when (action) {
            RuleEngine.ActionType.PAUSE -> controlVia(BulkAction.Power(PowerAction.PAUSE), entity)
            RuleEngine.ActionType.RESUME -> controlVia(BulkAction.Power(PowerAction.RESUME), entity)
            RuleEngine.ActionType.REBOOT -> controlVia(BulkAction.Reboot, entity)
            RuleEngine.ActionType.PLUG_OFF -> plugSwitch(entity, on = false)
            RuleEngine.ActionType.PLUG_ON -> plugSwitch(entity, on = true)
            RuleEngine.ActionType.NOTIFY -> "notified"
        }

    /** Same capability preflight as manual bulk actions: never send an unsupported command. */
    private suspend fun controlVia(action: BulkAction, entity: MinerEntity): String {
        val plan = fleetControl.plan(action, listOf(entity))
        if (plan.supported.isEmpty()) {
            return "skipped: ${plan.skipped.firstOrNull()?.second ?: "not supported"}"
        }
        return when (fleetControl.execute(plan).first().result) {
            is ActionResult.Success -> "ok"
            is ActionResult.Failure -> "failed"
            is ActionResult.Unsupported -> "unsupported"
            else -> "done"
        }
    }

    /** Every fire is announced (subject to alert quiet hours/cooldown) — actions are never silent. */
    private suspend fun announce(
        rule: RuleEntity,
        action: RuleEngine.ActionType,
        entity: MinerEntity,
        outcome: String,
    ) {
        val verb = when (action) {
            RuleEngine.ActionType.NOTIFY -> "matched"
            else -> "${action.label} → $outcome on"
        }
        alertRepository.raiseEvent(
            entity.id, entity.name, AlertType.RULE_TRIGGERED,
            "Watchdog \"${rule.label}\": $verb ${entity.name}." +
                if (rule.sustainedForMinutes > 0) " Condition held ${rule.sustainedForMinutes}+ min." else "",
            cooldownMs = rule.minIntervalMinutes * 60_000L,
        )
    }

    private suspend fun plugSwitch(entity: MinerEntity, on: Boolean): String {
        val type = PlugType.fromName(entity.plugType) ?: return "no plug"
        val plug = SmartPlugClient.Plug(type, entity.plugHost, entity.plugOnUrl, entity.plugOffUrl)
        val ok = if (on) smartPlugClient.turnOn(plug) else smartPlugClient.turnOff(plug)
        return if (ok) "plug ${if (on) "on" else "off"}" else "plug failed"
    }
}
