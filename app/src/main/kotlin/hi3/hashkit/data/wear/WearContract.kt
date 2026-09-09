package hi3.hashkit.data.wear

/**
 * Wearable Data Layer contract shared with the `:wear` module. The watch app declares an
 * identical copy (hi3.hashkit.wear.WearContract) — the two MUST stay in sync, since the
 * Data Layer matches on the path and key strings. Only a tiny, non-sensitive fleet summary
 * crosses this link: totals and counts, never addresses, credentials, or per-worker data.
 */
object WearContract {
    const val PATH_FLEET_SUMMARY = "/hi3/fleet_summary"

    const val KEY_TOTAL_HASHRATE_GHS = "total_hashrate_ghs"
    const val KEY_ONLINE = "online"
    const val KEY_OFFLINE = "offline"
    const val KEY_TOTAL = "total"

    /** Hottest chip temp across online miners, in °C; absent when unknown. */
    const val KEY_WORST_TEMP_C = "worst_temp_c"
    const val KEY_UPDATED_AT_MS = "updated_at_ms"

    /** The app's selected accent color (ARGB int), so the watch tile matches the UI theme. */
    const val KEY_ACCENT_ARGB = "accent_argb"
}
