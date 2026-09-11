package hi3.hashkit.domain.heat

/**
 * Pure heat-reuse economics. An ASIC miner is a resistive heater that also mines: essentially
 * 100% of the electrical power it draws leaves as heat. So its thermal output equals its
 * electrical input, and — if you would otherwise heat that space with resistive electric heat —
 * the heat is worth exactly what that electricity would have cost.
 *
 * All figures are ESTIMATES for planning. They assume the heat is actually useful (you're in
 * heating season and the miner is in a space you want warm); off-season the heat has no value.
 */
object HeatReuseMath {

    private const val BTU_PER_WH = 3.412
    private const val HOURS_PER_DAY = 24.0
    /** Average days per month (365.25 / 12). */
    const val DAYS_PER_MONTH = 30.4375

    /** Instantaneous heat output in BTU/hr (1 W ≈ 3.412 BTU/hr). */
    fun btuPerHour(powerW: Double?): Double? =
        powerW?.takeIf { it > 0 }?.let { it * BTU_PER_WH }

    /** Thermal energy delivered per day, in kWh (≈ electrical energy for a resistive load). */
    fun kwhThermalPerDay(powerW: Double?): Double? =
        powerW?.takeIf { it > 0 }?.let { it / 1000.0 * HOURS_PER_DAY }

    /**
     * Value of the delivered heat per day, versus resistive electric heating (1:1): the heat is
     * worth the electricity it would have taken to make it. Equals daily running cost — the point
     * being that during heating season the electricity isn't a pure mining cost.
     */
    fun heatingValuePerDay(powerW: Double?, ratePerKwh: Double?): Double? {
        val kwh = kwhThermalPerDay(powerW) ?: return null
        if (ratePerKwh == null || ratePerKwh <= 0) return null
        return kwh * ratePerKwh
    }

    /** Value of the delivered heat over an average month. */
    fun heatingValuePerMonth(powerW: Double?, ratePerKwh: Double?): Double? =
        heatingValuePerDay(powerW, ratePerKwh)?.let { it * DAYS_PER_MONTH }

    /**
     * Value of the same heat if you'd otherwise use a heat pump with coefficient of performance
     * [cop] (a heat pump delivers [cop]× the heat per unit electricity, so the miner only offsets
     * 1/[cop] of the cost). Use to avoid overstating savings for heat-pump homes.
     */
    fun heatingValuePerMonthVsHeatPump(powerW: Double?, ratePerKwh: Double?, cop: Double): Double? {
        if (cop <= 0) return null
        return heatingValuePerMonth(powerW, ratePerKwh)?.let { it / cop }
    }
}
