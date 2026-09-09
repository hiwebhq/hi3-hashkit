package hi3.hashkit.data.alerts

import hi3.hashkit.data.db.AlertDao
import hi3.hashkit.data.db.AlertEventEntity
import hi3.hashkit.data.db.AlertStateEntity
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.domain.alerts.AlertEvaluator
import hi3.hashkit.domain.alerts.AlertSignal
import hi3.hashkit.domain.alerts.AlertThresholds
import hi3.hashkit.domain.alerts.AlertType
import hi3.hashkit.domain.alerts.NotificationWindow
import hi3.hashkit.domain.analysis.AnomalyDetector
import hi3.hashkit.domain.model.MinerTelemetry
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the pure [AlertEvaluator] to each poll result, persists events, enforces
 * per-miner+type notification cooldowns, and sends recovery notifications. Notification
 * delivery (system + webhook) is suppressed during quiet hours — the event is still
 * recorded and appears in the alert list and the daily digest.
 */
@Singleton
class AlertRepository @Inject constructor(
    private val alertDao: AlertDao,
    private val notifier: AlertNotifier,
    private val webhookNotifier: WebhookNotifier,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), Long.serializer())

    /** True while the user's quiet-hours window is active (notifications suppressed). */
    private suspend fun isQuietNow(): Boolean {
        val s = settingsRepository.current()
        if (!s.quietHoursEnabled) return false
        val now = LocalDateTime.now()
        return NotificationWindow.isQuiet(now.hour * 60 + now.minute, s.quietStartMinute, s.quietEndMinute)
    }

    suspend fun processTelemetry(
        minerId: Long,
        minerName: String,
        telemetry: MinerTelemetry,
        expectedHashrateGhs: Double?,
        thresholds: AlertThresholds,
    ) {
        val stored = alertDao.state(minerId)
        val previous = stored?.toEvaluatorState() ?: AlertEvaluator.PreviousState()
        val lastNotified: MutableMap<String, Long> = stored?.lastNotifiedJson
            ?.let { runCatching { json.decodeFromString(mapSerializer, it) }.getOrNull() }
            ?.toMutableMap() ?: mutableMapOf()

        val signals = AlertEvaluator.evaluate(
            minerId, minerName, telemetry, expectedHashrateGhs, thresholds, previous,
        )
        val now = System.currentTimeMillis()
        val quiet = isQuietNow()

        for (signal in signals) {
            if (signal.active) {
                alertDao.insertEvent(
                    AlertEventEntity(
                        minerId = minerId,
                        minerName = minerName,
                        type = signal.type.name,
                        message = signal.message,
                        raisedAtEpochMs = now,
                        resolvedAtEpochMs = null,
                        acknowledged = false,
                    )
                )
                val last = lastNotified[signal.type.name] ?: 0L
                if (!quiet && now - last >= thresholds.cooldownMs) {
                    notifier.notify(signal)
                    webhookNotifier.send(signal)
                    lastNotified[signal.type.name] = now
                }
            } else {
                alertDao.resolveOpen(minerId, signal.type.name, now)
                if (!quiet) notifier.notify(signal)
                lastNotified.remove(signal.type.name)
            }
        }

        val next = AlertEvaluator.nextState(previous, telemetry, signals)
        alertDao.upsertState(
            AlertStateEntity(
                minerId = minerId,
                consecutiveFailures = next.consecutiveFailures,
                wasOffline = next.wasOffline,
                previousUptimeS = next.previousUptimeS,
                previousPoolUrl = next.previousPoolUrl,
                previousBestDifficulty = next.previousBestDifficulty,
                activeTypesCsv = next.activeTypes.joinToString(",") { it.name },
                lastNotifiedJson = json.encodeToString(mapSerializer, lastNotified),
            )
        )
    }

    /**
     * Raise a one-shot event alert that doesn't come from per-sample threshold evaluation
     * (plug cutoff, firmware update, performance anomaly). Deduplicated per miner+type via
     * the same cooldown map; notification suppressed during quiet hours.
     */
    suspend fun raiseEvent(
        minerId: Long,
        minerName: String,
        type: AlertType,
        message: String,
        cooldownMs: Long = 6 * 3_600_000L,
    ) {
        val stored = alertDao.state(minerId)
        val lastNotified = stored?.lastNotifiedJson
            ?.let { runCatching { json.decodeFromString(mapSerializer, it) }.getOrNull() }
            ?.toMutableMap() ?: mutableMapOf()
        val now = System.currentTimeMillis()
        val last = lastNotified[type.name] ?: 0L
        if (now - last < cooldownMs) return

        alertDao.insertEvent(
            AlertEventEntity(
                minerId = minerId,
                minerName = minerName,
                type = type.name,
                message = message,
                raisedAtEpochMs = now,
                resolvedAtEpochMs = null,
                acknowledged = false,
            )
        )
        val signal = AlertSignal(minerId, minerName, type, message, active = true)
        if (!isQuietNow()) {
            notifier.notify(signal)
            webhookNotifier.send(signal)
        }
        lastNotified[type.name] = now
        // Preserve the rest of the state row; only the notified map changed.
        alertDao.upsertState(
            (stored ?: AlertStateEntity(minerId = minerId)).copy(
                lastNotifiedJson = json.encodeToString(mapSerializer, lastNotified),
            )
        )
    }

    /**
     * Run statistical anomaly detection over a miner's recent history and raise a
     * [AlertType.PERFORMANCE_ANOMALY] when a gradual trend (hashrate drift / rising reject
     * rate) is found. Cheap and cooldown-guarded so it doesn't spam.
     */
    suspend fun processAnomalies(minerId: Long, minerName: String, history: List<MinerTelemetry>) {
        val findings = AnomalyDetector.analyze(history)
        if (findings.isEmpty()) return
        raiseEvent(
            minerId, minerName, AlertType.PERFORMANCE_ANOMALY,
            "$minerName: " + findings.joinToString("; ") { it.message },
        )
    }

    /**
     * If the daily digest is due, send one summary notification of the last 24h of alerts
     * and record the send time. No-op if disabled or already sent for today's window.
     */
    suspend fun maybeSendDigest() {
        val s = settingsRepository.current()
        if (!s.digestEnabled) return
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val todayDigest = LocalDateTime.now(zone)
            .withHour(s.digestHour).withMinute(0).withSecond(0).withNano(0)
            .atZone(zone).toInstant().toEpochMilli()
        if (!NotificationWindow.digestDue(now, todayDigest, s.lastDigestSentEpochMs)) return

        val events = alertDao.eventsSince(now - 24 * 3_600_000L)
            .filter { it.type != AlertType.NEW_BEST_DIFFICULTY.name }
        settingsRepository.setLastDigestSent(now)
        if (events.isEmpty()) {
            notifier.notifyDigest("Hi3 Hashkit — all quiet", "No alerts in the last 24 hours.")
            return
        }
        val byType = events.groupingBy { it.type }.eachCount()
        val body = buildString {
            append("${events.size} alert(s) in the last 24h:\n")
            byType.entries.sortedByDescending { it.value }.forEach { (type, count) ->
                append("• ${prettyType(type)}: $count\n")
            }
            val open = events.count { it.resolvedAtEpochMs == null }
            if (open > 0) append("$open still unresolved.")
        }.trim()
        notifier.notifyDigest("Hi3 Hashkit — daily digest", body)
    }

    private fun prettyType(type: String): String =
        type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun AlertStateEntity.toEvaluatorState() = AlertEvaluator.PreviousState(
        consecutiveFailures = consecutiveFailures,
        wasOffline = wasOffline,
        previousUptimeS = previousUptimeS,
        previousPoolUrl = previousPoolUrl,
        previousBestDifficulty = previousBestDifficulty,
        activeTypes = activeTypesCsv.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { name -> AlertType.entries.firstOrNull { it.name == name } }
            .toSet(),
    )
}
