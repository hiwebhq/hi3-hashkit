package hi3.hashkit.integrations.hi3

/**
 * Pools whose per-worker stats the app can read. Each is verified against a real API:
 *  - HI3 / PUBLIC_POOL: public-pool software, GET /api/client/{address} (workers[]).
 *  - CKPOOL: raw.stats.ckpool.org/users/{address} (worker[] with suffix-encoded hashrates).
 *  - OCEAN: api.ocean.xyz/v1/user_hashrate_full/{address} (workers with hashrate_60s/3600/10800).
 *
 * All are address/subaccount based and need no API key; an optional token/watcher field is
 * offered for pools that later require one. Per-worker names correlate to local miners.
 */
enum class PoolType(
    val displayName: String,
    val defaultBaseUrl: String,
    /** True when the base URL is user-editable (self-hostable public-pool instances). */
    val baseUrlEditable: Boolean,
    val identifierLabel: String,
) {
    HI3("Hi3 Pool", "https://pool.hi3.cc", baseUrlEditable = true, identifierLabel = "Payout address"),
    PUBLIC_POOL("Public Pool", "https://web.public-pool.io", baseUrlEditable = true, identifierLabel = "Payout address"),
    CKPOOL("CKPool", "https://raw.stats.ckpool.org", baseUrlEditable = false, identifierLabel = "Payout address"),
    OCEAN("OCEAN", "https://api.ocean.xyz", baseUrlEditable = false, identifierLabel = "Address / username");

    companion object {
        fun fromName(name: String?): PoolType =
            entries.firstOrNull { it.name == name } ?: HI3
    }
}
