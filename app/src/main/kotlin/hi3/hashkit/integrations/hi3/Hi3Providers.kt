package hi3.hashkit.integrations.hi3

/**
 * Hi3 platform integrations.
 *
 * Hi3 POOL: implemented ([Hi3PoolClient]/[Hi3PoolRepository]) — opt-in, read-only,
 * address-keyed queries against the pool's verified public-pool-fork API.
 *
 * Hi3 MMP: the read-only fleet view is implemented ([MmpClient]/[MmpRepository]) —
 * opt-in, Bearer-key auth, key stored via Android Keystore. An AGENT-side surface
 * (registering this device as a site agent, uploading telemetry, receiving remote
 * commands) was DECLINED by the user and has no implementation — the phone never
 * uploads miner data. If ever built, it must be opt-in with a full data disclosure.
 * (The former placeholder interface was deleted so an unimplemented `uploadTelemetry`
 * signature doesn't sit in a privacy-sensitive package looking like a live capability.)
 */
