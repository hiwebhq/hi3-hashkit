package hi3.hashkit.domain.rules

import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry

/**
 * Pure evaluation for the automation rules engine ("if condition then action"). Deciding
 * *whether* a rule matches a miner is separated from *performing* the action (which the
 * runner does with the control/plug/alert repositories), so this stays unit-testable.
 */
object RuleEngine {

    enum class ConditionType(val label: String, val needsThreshold: Boolean, val unit: String) {
        CHIP_TEMP_ABOVE("Chip temp above", true, "°C"),
        VR_TEMP_ABOVE("VR temp above", true, "°C"),
        HASHRATE_BELOW_PCT("Hashrate below % of expected", true, "%"),
        REJECT_RATE_ABOVE("Reject rate above", true, "%"),
        OFFLINE("Miner offline", false, "");

        companion object {
            fun fromName(n: String?): ConditionType? = entries.firstOrNull { it.name == n }
        }
    }

    enum class ActionType(val label: String) {
        PAUSE("Pause hashing"),
        RESUME("Resume hashing"),
        REBOOT("Reboot"),
        PLUG_OFF("Smart plug OFF"),
        PLUG_ON("Smart plug ON"),
        NOTIFY("Notify only");

        companion object {
            fun fromName(n: String?): ActionType? = entries.firstOrNull { it.name == n }
        }
    }

    /**
     * Does [telemetry]/[status] satisfy the condition? [threshold] is required for the
     * threshold conditions; [expectedHashrateGhs] is the miner's expected hashrate (for the
     * percentage condition). Missing data means "no match" — a rule never fires on unknowns.
     */
    fun matches(
        type: ConditionType,
        threshold: Double?,
        telemetry: MinerTelemetry,
        status: MinerStatus,
        expectedHashrateGhs: Double?,
    ): Boolean = when (type) {
        ConditionType.OFFLINE -> status == MinerStatus.OFFLINE
        ConditionType.CHIP_TEMP_ABOVE -> {
            val v = telemetry.chipTempC.value
            threshold != null && v != null && v >= threshold
        }
        ConditionType.VR_TEMP_ABOVE -> {
            val v = telemetry.vrTempC.value
            threshold != null && v != null && v > 1.0 && v >= threshold
        }
        ConditionType.HASHRATE_BELOW_PCT -> {
            if (status == MinerStatus.OFFLINE) false else {
                val expected = expectedHashrateGhs ?: telemetry.expectedHashrateGhs.value
                val actual = telemetry.hashrateGhs.value
                threshold != null && expected != null && expected > 0 && actual != null &&
                    (actual / expected * 100.0) < threshold
            }
        }
        ConditionType.REJECT_RATE_ABOVE -> {
            val acc = telemetry.sharesAccepted
            val rej = telemetry.sharesRejected
            if (threshold != null && acc != null && rej != null && acc + rej > 100) {
                (rej.toDouble() / (acc + rej) * 100.0) > threshold
            } else false
        }
    }
}
