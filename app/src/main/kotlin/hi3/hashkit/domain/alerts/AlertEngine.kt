package hi3.hashkit.domain.alerts

import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry

enum class AlertType {
    MINER_OFFLINE,
    HASHRATE_BELOW_THRESHOLD,
    CHIP_OVER_TEMP,
    VR_OVER_TEMP,
    FAN_STOPPED,
    REJECT_RATE_HIGH,
    UNEXPECTED_RESTART,
    POOL_CHANGED,
    NEW_BEST_DIFFICULTY,
    /** Primary pool unreachable — the miner fell back to a backup pool. */
    POOL_DISCONNECTED,
    /** Statistical trend (hashrate drift / rising reject rate) from AnomalyDetector. */
    PERFORMANCE_ANOMALY,
    /** The over-temp smart-plug safety cutoff switched a miner's plug off. */
    PLUG_CUTOFF,
    /** A newer firmware release is available for this miner. */
    FIRMWARE_UPDATE_AVAILABLE,
    /** A share met the network target — a block was (very likely) found. Celebrate! */
    BLOCK_FOUND,
}

/** Global alert thresholds; per-miner values override via [AlertOverrides]. */
data class AlertThresholds(
    val offlineAfterFailures: Int = 2,
    val hashrateBelowPercent: Double = 80.0,
    val chipTempC: Double = 70.0,
    val vrTempC: Double = 90.0,
    val rejectRatePercent: Double = 3.0,
    /** Suppress duplicate alerts of the same type per miner for this long. */
    val cooldownMs: Long = 30 * 60_000L,
)

/** Per-miner overrides; null fields fall back to the global threshold. */
data class AlertOverrides(
    val hashrateBelowPercent: Double? = null,
    val chipTempC: Double? = null,
    val vrTempC: Double? = null,
    val rejectRatePercent: Double? = null,
    /** Muted miners are still polled and charted but raise no alerts. */
    val muted: Boolean = false,
) {
    val isEmpty: Boolean
        get() = hashrateBelowPercent == null && chipTempC == null &&
            vrTempC == null && rejectRatePercent == null && !muted
}

/** Effective thresholds for one miner: global values with per-miner overrides applied. */
fun AlertThresholds.withOverrides(overrides: AlertOverrides?): AlertThresholds =
    if (overrides == null) this else copy(
        hashrateBelowPercent = overrides.hashrateBelowPercent ?: hashrateBelowPercent,
        chipTempC = overrides.chipTempC ?: chipTempC,
        vrTempC = overrides.vrTempC ?: vrTempC,
        rejectRatePercent = overrides.rejectRatePercent ?: rejectRatePercent,
    )

/** A newly raised or resolved condition produced by one evaluation pass. */
data class AlertSignal(
    val minerId: Long,
    val minerName: String,
    val type: AlertType,
    val message: String,
    /** true = condition active (raise), false = condition cleared (recovery). */
    val active: Boolean,
)

/**
 * Pure alert evaluation: compares one telemetry sample against thresholds and the
 * previous sample's state. Deduplication/cooldown live in the repository layer, which
 * knows what has already been raised; this class only detects conditions and
 * transitions, so it is fully unit-testable.
 */
object AlertEvaluator {

    data class PreviousState(
        val consecutiveFailures: Int = 0,
        val wasOffline: Boolean = false,
        val previousUptimeS: Long? = null,
        val previousPoolUrl: String? = null,
        val previousBestDifficulty: Double? = null,
        val activeTypes: Set<AlertType> = emptySet(),
    )

    fun evaluate(
        minerId: Long,
        minerName: String,
        telemetry: MinerTelemetry,
        expectedHashrateGhs: Double?,
        thresholds: AlertThresholds,
        previous: PreviousState,
    ): List<AlertSignal> {
        val signals = mutableListOf<AlertSignal>()
        val offline = telemetry.status == MinerStatus.OFFLINE

        fun condition(type: AlertType, active: Boolean, message: String) {
            if (active && type !in previous.activeTypes) {
                signals += AlertSignal(minerId, minerName, type, message, active = true)
            } else if (!active && type in previous.activeTypes) {
                signals += AlertSignal(minerId, minerName, type, message, active = false)
            }
        }

        // Offline requires consecutive failures to avoid flapping on one dropped poll.
        val failures = if (offline) previous.consecutiveFailures + 1 else 0
        condition(
            AlertType.MINER_OFFLINE,
            active = failures >= thresholds.offlineAfterFailures,
            message = if (failures >= thresholds.offlineAfterFailures)
                "$minerName is offline (${failures} consecutive failed polls)."
            else "$minerName is back online.",
        )

        if (!offline) {
            val expected = expectedHashrateGhs ?: telemetry.expectedHashrateGhs.value
            val actual = telemetry.hashrateGhs.value
            if (expected != null && expected > 0 && actual != null) {
                val pct = actual / expected * 100.0
                condition(
                    AlertType.HASHRATE_BELOW_THRESHOLD,
                    active = pct < thresholds.hashrateBelowPercent,
                    message = if (pct < thresholds.hashrateBelowPercent)
                        "$minerName hashrate is at ${pct.toInt()}% of expected."
                    else "$minerName hashrate recovered to ${pct.toInt()}% of expected.",
                )
            }

            telemetry.chipTempC.value?.let { t ->
                condition(
                    AlertType.CHIP_OVER_TEMP,
                    active = t >= thresholds.chipTempC,
                    message = if (t >= thresholds.chipTempC)
                        "$minerName chip temperature ${t.toInt()}°C exceeds ${thresholds.chipTempC.toInt()}°C."
                    else "$minerName chip temperature back below ${thresholds.chipTempC.toInt()}°C.",
                )
            }

            telemetry.vrTempC.value?.takeIf { it > 1.0 }?.let { t ->
                condition(
                    AlertType.VR_OVER_TEMP,
                    active = t >= thresholds.vrTempC,
                    message = if (t >= thresholds.vrTempC)
                        "$minerName VR temperature ${t.toInt()}°C exceeds ${thresholds.vrTempC.toInt()}°C."
                    else "$minerName VR temperature back below ${thresholds.vrTempC.toInt()}°C.",
                )
            }

            val stalledFan = telemetry.fans.firstOrNull { it.rpm == 0 && (it.percent ?: 0) > 0 }
            condition(
                AlertType.FAN_STOPPED,
                active = stalledFan != null,
                message = stalledFan?.let { "$minerName fan ${it.index + 1} is reporting zero RPM." }
                    ?: "$minerName fans are spinning again.",
            )

            // Primary pool unreachable: the miner reports it's running on its fallback pool.
            telemetry.usingFallbackPool?.let { onFallback ->
                condition(
                    AlertType.POOL_DISCONNECTED,
                    active = onFallback,
                    message = if (onFallback)
                        "$minerName lost its primary pool and is mining on a fallback."
                    else "$minerName reconnected to its primary pool.",
                )
            }

            val accepted = telemetry.sharesAccepted
            val rejected = telemetry.sharesRejected
            if (accepted != null && rejected != null && accepted + rejected > 100) {
                val rate = rejected.toDouble() / (accepted + rejected) * 100.0
                condition(
                    AlertType.REJECT_RATE_HIGH,
                    active = rate > thresholds.rejectRatePercent,
                    message = if (rate > thresholds.rejectRatePercent)
                        "$minerName rejected-share rate ${"%.1f".format(rate)}% exceeds ${"%.1f".format(thresholds.rejectRatePercent)}%."
                    else "$minerName rejected-share rate back to normal.",
                )
            }

            // One-shot events (no recovery counterpart): restart, pool change, best diff.
            val uptime = telemetry.uptimeSeconds
            if (uptime != null && previous.previousUptimeS != null && uptime < previous.previousUptimeS) {
                signals += AlertSignal(
                    minerId, minerName, AlertType.UNEXPECTED_RESTART,
                    "$minerName restarted (uptime reset from ${previous.previousUptimeS / 60}m to ${uptime / 60}m).",
                    active = true,
                )
            }

            val pool = telemetry.poolUrl
            if (pool != null && previous.previousPoolUrl != null && pool != previous.previousPoolUrl) {
                signals += AlertSignal(
                    minerId, minerName, AlertType.POOL_CHANGED,
                    "$minerName pool changed from ${previous.previousPoolUrl} to $pool.",
                    active = true,
                )
            }

            val best = telemetry.bestDifficulty
            if (best != null && previous.previousBestDifficulty != null && best > previous.previousBestDifficulty) {
                signals += AlertSignal(
                    minerId, minerName, AlertType.NEW_BEST_DIFFICULTY,
                    "$minerName found a new best difficulty: ${hi3.hashkit.core.Units.formatDifficulty(best)}.",
                    active = true,
                )
            }
        }

        return signals
    }

    /** Next persisted state after this evaluation. */
    fun nextState(previous: PreviousState, telemetry: MinerTelemetry, signals: List<AlertSignal>): PreviousState {
        val offline = telemetry.status == MinerStatus.OFFLINE
        val newActive = previous.activeTypes.toMutableSet()
        for (s in signals) {
            if (s.type in RECOVERABLE) {
                if (s.active) newActive += s.type else newActive -= s.type
            }
        }
        return PreviousState(
            consecutiveFailures = if (offline) previous.consecutiveFailures + 1 else 0,
            wasOffline = offline,
            previousUptimeS = telemetry.uptimeSeconds ?: previous.previousUptimeS,
            previousPoolUrl = telemetry.poolUrl ?: previous.previousPoolUrl,
            previousBestDifficulty = telemetry.bestDifficulty ?: previous.previousBestDifficulty,
            activeTypes = newActive,
        )
    }

    val RECOVERABLE = setOf(
        AlertType.MINER_OFFLINE,
        AlertType.HASHRATE_BELOW_THRESHOLD,
        AlertType.CHIP_OVER_TEMP,
        AlertType.VR_OVER_TEMP,
        AlertType.FAN_STOPPED,
        AlertType.REJECT_RATE_HIGH,
        AlertType.POOL_DISCONNECTED,
    )
}
