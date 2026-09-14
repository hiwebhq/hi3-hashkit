package hi3.hashkit.domain.solo

/**
 * Plain-language economics for a single home miner: what it costs per month, what pool
 * mining would earn, and whether the hardware ever pays for itself. Built on [ProfitMath]
 * so every figure carries the same caveats — estimates from difficulty, price and your
 * electricity rate, excluding pool and transaction fees. Honest by construction: the
 * payback answer is "never" whenever expected earnings don't cover the power bill.
 */
object HomeEconomics {

    /** Average days per month (365.25 / 12). */
    const val DAYS_PER_MONTH = 30.4375

    sealed interface Payback {
        /** Months until cumulative net earnings reach the purchase price. */
        data class Months(val months: Double) : Payback

        /** Net earnings are zero or negative: the purchase is never recovered. */
        data object Never : Payback
    }

    fun monthlyKwh(powerW: Double?): Double? =
        ProfitMath.energyKwhPerDay(powerW)?.times(DAYS_PER_MONTH)

    fun monthlyCost(powerW: Double?, ratePerKwh: Double?): Double? =
        ProfitMath.powerCostPerDay(powerW, ratePerKwh)?.times(DAYS_PER_MONTH)

    /** Expected pool-mining revenue per month; null unless difficulty AND a price are known. */
    fun monthlyRevenue(hashrateGhs: Double?, difficulty: Double?, btcPrice: Double?): Double? =
        ProfitMath.revenuePerDay(ProfitMath.btcPerDay(hashrateGhs, difficulty), btcPrice)
            ?.times(DAYS_PER_MONTH)

    /** Net per month; null unless both revenue and cost are known (same rule as [ProfitMath.netPerDay]). */
    fun monthlyNet(revenue: Double?, cost: Double?): Double? = ProfitMath.netPerDay(revenue, cost)

    /**
     * Payback on [purchasePrice] at [netPerMonth]. Null when either input is unknown or the
     * price is not positive; [Payback.Never] when the miner doesn't clear its power bill.
     */
    fun payback(purchasePrice: Double?, netPerMonth: Double?): Payback? {
        if (purchasePrice == null || purchasePrice <= 0 || netPerMonth == null) return null
        if (netPerMonth <= 0) return Payback.Never
        return Payback.Months(purchasePrice / netPerMonth)
    }
}
