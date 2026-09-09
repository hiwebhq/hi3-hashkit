package hi3.hashkit.data.alerts

import hi3.hashkit.data.db.AlertDao
import hi3.hashkit.data.db.AlertEventEntity
import hi3.hashkit.data.db.AlertStateEntity
import hi3.hashkit.domain.alerts.AlertEvaluator
import hi3.hashkit.domain.alerts.AlertThresholds
import hi3.hashkit.domain.alerts.AlertType
import hi3.hashkit.domain.model.MinerTelemetry
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the pure [AlertEvaluator] to each poll result, persists events, enforces
 * per-miner+type notification cooldowns, and sends recovery notifications.
 */
@Singleton
class AlertRepository @Inject constructor(
    private val alertDao: AlertDao,
    private val notifier: AlertNotifier,
    private val webhookNotifier: WebhookNotifier,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), Long.serializer())

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
                if (now - last >= thresholds.cooldownMs) {
                    notifier.notify(signal)
                    webhookNotifier.send(signal)
                    lastNotified[signal.type.name] = now
                }
            } else {
                alertDao.resolveOpen(minerId, signal.type.name, now)
                notifier.notify(signal)
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
