package hi3.hashkit.data.schedule

import hi3.hashkit.data.db.MinerEntity
import hi3.hashkit.data.db.ScheduleDao
import hi3.hashkit.data.db.ScheduleEntity
import hi3.hashkit.data.repo.BulkAction
import hi3.hashkit.data.repo.FleetControl
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.domain.adapter.FanControl
import hi3.hashkit.integrations.plug.PlugType
import hi3.hashkit.integrations.plug.SmartPlugClient
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Pure due-time logic, unit-testable without Android or a clock. */
object ScheduleDueLogic {

    /**
     * A schedule is due when: enabled, today is a selected day, the scheduled time has
     * passed today, it has not already run in today's window, and the min-interval
     * since the last run has elapsed. Missed windows (device asleep) fire on the next
     * evaluation the same day; a window missed entirely is skipped, never replayed.
     */
    fun isDue(schedule: ScheduleEntity, now: LocalDateTime, zone: ZoneId): Boolean {
        if (!schedule.enabled) return false
        val days = schedule.daysOfWeekCsv.split(",")
            .mapNotNull { runCatching { DayOfWeek.valueOf(it.trim()) }.getOrNull() }
        if (days.isNotEmpty() && now.dayOfWeek !in days) return false
        val minutesNow = now.hour * 60 + now.minute
        if (minutesNow < schedule.timeMinutesOfDay) return false

        val lastRun = schedule.lastRunAtEpochMs
        if (lastRun != null) {
            val lastRunLocal = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(lastRun), zone,
            )
            // Already ran in today's window?
            if (lastRunLocal.toLocalDate() == now.toLocalDate() &&
                lastRunLocal.hour * 60 + lastRunLocal.minute >= schedule.timeMinutesOfDay
            ) return false
            // Min-interval guard across windows.
            val minutesSince = java.time.Duration.between(lastRunLocal, now).toMinutes()
            if (minutesSince < schedule.minIntervalMinutes) return false
        }
        return true
    }

    fun actionOf(schedule: ScheduleEntity): BulkAction? {
        val params = runCatching {
            Json.parseToJsonElement(schedule.paramsJson).jsonObject
        }.getOrNull()
        return when (schedule.actionType) {
            "reboot" -> BulkAction.Reboot
            // Time-of-use: pause during peak-rate hours, resume off-peak.
            "pause" -> BulkAction.Power(hi3.hashkit.domain.adapter.PowerAction.PAUSE)
            "resume" -> BulkAction.Power(hi3.hashkit.domain.adapter.PowerAction.RESUME)
            "set_pool" -> {
                val url = params?.get("url")?.jsonPrimitive?.content ?: return null
                val port = params["port"]?.jsonPrimitive?.intOrNull ?: return null
                val worker = params["worker"]?.jsonPrimitive?.content ?: return null
                BulkAction.SetPool(url, port, worker)
            }
            "set_fan" -> {
                val auto = params?.get("auto")?.jsonPrimitive?.booleanOrNull ?: return null
                if (auto) BulkAction.SetFan(FanControl.Automatic())
                else BulkAction.SetFan(
                    FanControl.Manual(params["percent"]?.jsonPrimitive?.intOrNull ?: return null)
                )
            }
            else -> null
        }
    }

    /** Plug actions drive the miner's configured smart plug directly, not a miner control API. */
    fun isPlugAction(schedule: ScheduleEntity): Boolean =
        schedule.actionType == "plug_on" || schedule.actionType == "plug_off"
}

/**
 * Evaluates schedules on each poll tick (foreground) and each background work cycle.
 * Not exact-time: execution granularity is the polling/work cadence, which the
 * schedules UI states honestly.
 */
@Singleton
class ScheduleEngine @Inject constructor(
    private val scheduleDao: ScheduleDao,
    private val minerRepository: MinerRepository,
    private val fleetControl: FleetControl,
    private val smartPlugClient: SmartPlugClient,
) {
    suspend fun runDueSchedules() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        for (schedule in scheduleDao.enabled()) {
            if (!ScheduleDueLogic.isDue(schedule, now, zone)) continue
            if (ScheduleDueLogic.isPlugAction(schedule)) {
                runPlugSchedule(schedule)
                continue
            }
            val action = ScheduleDueLogic.actionOf(schedule)
            if (action == null) {
                scheduleDao.recordRun(schedule.id, System.currentTimeMillis(), "invalid action")
                continue
            }
            val targets = resolveTargets(schedule)
            val plan = fleetControl.plan(action, targets)
            val outcomes = fleetControl.execute(plan)
            val ok = outcomes.count { it.result is ActionResult.Success }
            val failed = outcomes.size - ok
            scheduleDao.recordRun(
                schedule.id,
                System.currentTimeMillis(),
                "$ok ok, $failed failed, ${plan.skipped.size} skipped",
            )
        }
    }

    /**
     * Switch each target miner's configured smart plug on or off. Miners without a plug
     * configured are skipped (counted, not failed). Turning power back on is normally a
     * manual action, but a user-authored scheduled `plug_on` is an explicit opt-in.
     */
    private suspend fun runPlugSchedule(schedule: ScheduleEntity) {
        val on = schedule.actionType == "plug_on"
        val targets = resolveTargets(schedule)
        var ok = 0
        var failed = 0
        var skipped = 0
        for (entity in targets) {
            val type = PlugType.fromName(entity.plugType)
            if (type == null) { skipped++; continue }
            val plug = SmartPlugClient.Plug(type, entity.plugHost, entity.plugOnUrl, entity.plugOffUrl)
            val success = if (on) smartPlugClient.turnOn(plug) else smartPlugClient.turnOff(plug)
            if (success) ok++ else failed++
        }
        scheduleDao.recordRun(
            schedule.id,
            System.currentTimeMillis(),
            "plug ${if (on) "on" else "off"}: $ok ok, $failed failed, $skipped no-plug",
        )
    }

    private suspend fun resolveTargets(schedule: ScheduleEntity): List<MinerEntity> {
        val all = minerRepository.observeMinerEntities().first().filter { !it.isDemo }
        val ids = schedule.targetMinerIdsCsv.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
        return when {
            ids.isNotEmpty() -> all.filter { it.id in ids }
            !schedule.targetGroup.isNullOrBlank() -> all.filter { it.groupName == schedule.targetGroup }
            else -> all
        }
    }
}
