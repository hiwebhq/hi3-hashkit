package hi3.hashkit.adapters.espminer

import hi3.hashkit.domain.model.MinerIdentity

/**
 * Firmware flavor detection. Controls are enabled ONLY for official ESP-Miner/AxeOS
 * v2.x, whose endpoints were verified against the tagged firmware source:
 *
 *  - v2.0–v2.14: pools via flat PATCH fields (stratumURL/stratumPort/stratumUser).
 *  - v2.15+: pools via a "pools" array where echoing the mask "*****" preserves the
 *    stored stratumPassword (verified in http_server.c) and omitting it would WIPE it,
 *    so the app always echoes every field it is not changing.
 *  - All v2.x: POST /api/system/restart; fan via autofanspeed/manualFanSpeed/temptarget;
 *    tune via frequency/coreVoltage constrained to GET /api/system/asic options.
 *
 * NerdQAxe (e.g. NerdQAxe++) and other forks share the info endpoint but have
 * unverified control semantics (NerdQAxe's autofanspeed is a mode enum, not a bool) —
 * they stay monitoring-only until verified separately.
 */
enum class EspMinerFlavor {
    OFFICIAL_V2_FLAT_POOLS,
    OFFICIAL_V2_POOLS_ARRAY,
    NERDQAXE,
    UNKNOWN_FORK,
}

object EspMinerFirmware {

    fun flavorOf(identity: MinerIdentity?): EspMinerFlavor {
        val model = identity?.model.orEmpty()
        if (model.contains("NerdQAxe", ignoreCase = true) ||
            model.contains("NerdAxe", ignoreCase = true)
        ) {
            return EspMinerFlavor.NERDQAXE
        }
        val version = identity?.firmwareVersion.orEmpty()
        val match = Regex("^v2\\.(\\d+)").find(version) ?: return EspMinerFlavor.UNKNOWN_FORK
        val minor = match.groupValues[1].toIntOrNull() ?: return EspMinerFlavor.UNKNOWN_FORK
        return if (minor >= 15) EspMinerFlavor.OFFICIAL_V2_POOLS_ARRAY
        else EspMinerFlavor.OFFICIAL_V2_FLAT_POOLS
    }

    fun controlsSupported(flavor: EspMinerFlavor): Boolean = when (flavor) {
        EspMinerFlavor.OFFICIAL_V2_FLAT_POOLS, EspMinerFlavor.OFFICIAL_V2_POOLS_ARRAY -> true
        EspMinerFlavor.NERDQAXE, EspMinerFlavor.UNKNOWN_FORK -> false
    }

    /**
     * Reboot (`POST /api/system/restart`) is inherited unchanged from ESP-Miner, so it's
     * safe on NerdQAxe too — unlike pool/fan/tune whose semantics differ on the fork.
     */
    fun rebootSupported(flavor: EspMinerFlavor): Boolean =
        controlsSupported(flavor) || flavor == EspMinerFlavor.NERDQAXE

    const val UNVERIFIED_REASON =
        "This firmware's control API has not been verified; monitoring only until it is."
}
