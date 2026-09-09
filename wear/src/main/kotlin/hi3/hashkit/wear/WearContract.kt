package hi3.hashkit.wear

/**
 * Wearable Data Layer contract — MUST stay identical to the phone's copy at
 * hi3.hashkit.data.wear.WearContract. The Data Layer matches on these exact strings.
 */
object WearContract {
    const val PATH_FLEET_SUMMARY = "/hi3/fleet_summary"

    const val KEY_TOTAL_HASHRATE_GHS = "total_hashrate_ghs"
    const val KEY_ONLINE = "online"
    const val KEY_OFFLINE = "offline"
    const val KEY_TOTAL = "total"
    const val KEY_WORST_TEMP_C = "worst_temp_c"
    const val KEY_UPDATED_AT_MS = "updated_at_ms"
}
