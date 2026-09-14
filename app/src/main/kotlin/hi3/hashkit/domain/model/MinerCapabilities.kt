package hi3.hashkit.domain.model

/**
 * Explicit capability set reported by each adapter. The UI hides or disables anything
 * not present here and explains why. Nothing is ever assumed.
 */
enum class Capability {
    TELEMETRY,
    LOGS,
    SET_POOLS,
    SET_OPERATING_MODE,
    SET_FAN,
    APPLY_APPROVED_TUNE,
    REBOOT,
    POWER_CONTROL,
    /** Blink a locate light so the physical unit can be found in a rack. */
    LOCATE,
    /** Rotate / invert / time out the unit's own little screen (Bitaxe OLED). */
    SET_DISPLAY,
}

data class MinerCapabilities(
    val supported: Set<Capability>,
    /** Human-readable reasons for notable unsupported capabilities, keyed by capability. */
    val unsupportedReasons: Map<Capability, String> = emptyMap(),
) {
    operator fun contains(c: Capability): Boolean = c in supported

    companion object {
        /** Monitoring only — the safe default while a family's controls are unverified. */
        fun monitoringOnly(reason: String): MinerCapabilities = MinerCapabilities(
            supported = setOf(Capability.TELEMETRY),
            unsupportedReasons = Capability.entries
                .filter { it != Capability.TELEMETRY }
                .associateWith { reason },
        )
    }
}
