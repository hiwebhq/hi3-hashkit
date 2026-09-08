package hi3.hashkit.domain.solo

/**
 * Pure economics for pool mining. Estimates only — they depend on network difficulty, the
 * BTC price, the block subsidy and your electricity rate, and exclude transaction fees and
 * pool fees. Always surfaced as ESTIMATED.
 */
object ProfitMath {

    /** Current block subsidy in BTC (post-2024 halving). */
    const val BLOCK_SUBSIDY_BTC = 3.125

    private const val TWO_POW_32 = 4_294_967_296.0
    private const val SECONDS_PER_DAY = 86_400.0

    /**
     * Expected BTC/day for [hashrateGhs] at [difficulty], proportional to your share of the
     * network. Uses the block subsidy only (fees excluded). Returns null on bad inputs.
     */
    fun btcPerDay(hashrateGhs: Double?, difficulty: Double?, subsidyBtc: Double = BLOCK_SUBSIDY_BTC): Double? {
        if (hashrateGhs == null || difficulty == null || hashrateGhs <= 0 || difficulty <= 0) return null
        val hashrateHs = hashrateGhs * 1e9
        // blocks/day the miner is expected to find = hashrate / (difficulty * 2^32) * seconds/day
        val blocksPerDay = hashrateHs / (difficulty * TWO_POW_32) * SECONDS_PER_DAY
        return blocksPerDay * subsidyBtc
    }

    /** Electricity cost per day: kW × 24h × rate. */
    fun powerCostPerDay(powerW: Double?, ratePerKwh: Double?): Double? {
        if (powerW == null || ratePerKwh == null || powerW <= 0 || ratePerKwh <= 0) return null
        return powerW / 1000.0 * 24.0 * ratePerKwh
    }

    /** Energy per day in kWh. */
    fun energyKwhPerDay(powerW: Double?): Double? =
        powerW?.takeIf { it > 0 }?.let { it / 1000.0 * 24.0 }

    /** Heat output in BTU/hr (1 W ≈ 3.412 BTU/hr) — for heat-reuse planning. */
    fun heatBtuPerHour(powerW: Double?): Double? =
        powerW?.takeIf { it > 0 }?.let { it * 3.412 }
}
