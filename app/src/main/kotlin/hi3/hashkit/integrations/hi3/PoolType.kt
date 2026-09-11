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
    /** True when the required credential is the token/API-key field, not the address field. */
    val usesToken: Boolean = false,
    /** Placeholder pools have no verified endpoint yet — the app contacts nothing. */
    val comingSoon: Boolean = false,
    /** Documented public stratum host for the pool speed test; null = not benchmarkable. */
    val stratumHost: String? = null,
    val stratumPort: Int = 3333,
) {
    HI3("Hi3 Pool", "https://pool.hi3.cc", baseUrlEditable = true, identifierLabel = "Payout address",
        // pool.hi3.cc is Cloudflare-proxied (HTTP/HTTPS only), so stratum lives on a separate
        // unproxied host. Requires a DNS-only A record: stratum.hi3.cc -> stratum origin IP.
        stratumHost = "stratum.hi3.cc", stratumPort = 3333),
    PUBLIC_POOL("Public Pool", "https://web.public-pool.io", baseUrlEditable = true, identifierLabel = "Payout address",
        stratumHost = "public-pool.io", stratumPort = 21496),
    CKPOOL("CKPool", "https://raw.stats.ckpool.org", baseUrlEditable = false, identifierLabel = "Payout address",
        stratumHost = "solo.ckpool.org", stratumPort = 3333),
    OCEAN("OCEAN", "https://api.ocean.xyz", baseUrlEditable = false, identifierLabel = "Address / username",
        stratumHost = "mine.ocean.xyz", stratumPort = 3334),
    F2POOL("F2Pool", "https://api.f2pool.com", baseUrlEditable = false, identifierLabel = "Mining account / username",
        stratumHost = "btc.f2pool.com", stratumPort = 3333),
    BRAIINS("Braiins Pool", "https://pool.braiins.com", baseUrlEditable = false, identifierLabel = "Username (optional)", usesToken = true,
        stratumHost = "stratum.braiins.com", stratumPort = 3333),

    // Endpoints not yet verified — shown but do nothing. See Hi3PoolRepository/fetchAccountFor.
    LUXOR("Luxor (coming soon)", "https://app.luxor.tech", baseUrlEditable = false, identifierLabel = "Subaccount", comingSoon = true),
    NICEHASH("NiceHash (coming soon)", "https://api2.nicehash.com", baseUrlEditable = false, identifierLabel = "Organization ID", comingSoon = true);

    companion object {
        fun fromName(name: String?): PoolType =
            entries.firstOrNull { it.name == name } ?: HI3
    }
}
