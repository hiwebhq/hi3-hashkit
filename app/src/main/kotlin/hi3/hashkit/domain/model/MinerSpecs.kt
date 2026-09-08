package hi3.hashkit.domain.model

/**
 * Static reference data: nominal (manufacturer-rated) hashrate and power per model, from
 * public spec sheets. Used only as a FALLBACK — when a device or the user doesn't provide
 * an expected hashrate, the nominal value drives attainment %; when power is unknown, the
 * nominal watts can seed an ESTIMATED efficiency. Device-reported values always win.
 *
 * This is reference data, not an API — there are no endpoints here, so it carries no
 * invented-endpoint risk. Matching is by case-insensitive substring on the model string,
 * most specific entries first.
 */
object MinerSpecs {

    data class Spec(val nominalHashrateGhs: Double, val nominalPowerW: Double)

    /** Ordered most-specific → most-general so the first substring match wins. */
    private val TABLE: List<Pair<String, Spec>> = listOf(
        // Bitaxe (usually reports its own expected; included for completeness)
        "gamma turbo" to Spec(1_500.0, 22.0),
        "gamma" to Spec(1_200.0, 18.0),
        "supra" to Spec(700.0, 15.0),
        "ultra" to Spec(500.0, 12.0),
        // Antminer S21 family
        "s21 xp" to Spec(270_000.0, 3_645.0),
        "s21 pro" to Spec(234_000.0, 3_510.0),
        "s21+ hyd" to Spec(319_000.0, 4_785.0),
        "s21+" to Spec(216_000.0, 3_564.0),
        "s21 hyd" to Spec(335_000.0, 5_360.0),
        "s21" to Spec(200_000.0, 3_500.0),
        // Antminer S19 family
        "s19 xp" to Spec(141_000.0, 3_032.0),
        "s19j pro" to Spec(104_000.0, 3_068.0),
        "s19 pro" to Spec(110_000.0, 3_250.0),
        "s19" to Spec(95_000.0, 3_250.0),
        // Antminer S17 family (varies widely; conservative)
        "s17 pro" to Spec(53_000.0, 2_094.0),
        "s17" to Spec(56_000.0, 2_520.0),
        // WhatsMiner (common)
        "m60" to Spec(172_000.0, 3_344.0),
        "m50" to Spec(118_000.0, 3_276.0),
        "m30s++" to Spec(112_000.0, 3_472.0),
        "m30s+" to Spec(100_000.0, 3_400.0),
        // Canaan Avalon
        "avalon q" to Spec(90_000.0, 1_674.0),
        "nano 3s" to Spec(6_000.0, 140.0),
        "nano3s" to Spec(6_000.0, 140.0),
        "nano 3" to Spec(4_000.0, 140.0),
        "nano3" to Spec(4_000.0, 140.0),
        "avalon mini 3" to Spec(37_500.0, 800.0),
        // FutureBit Apollo
        "apollo" to Spec(3_800.0, 200.0),
        // Braiins Mini Miner
        "bmm 101" to Spec(1_000.0, 40.0),
        "bmm 100" to Spec(1_000.0, 40.0),
    )

    fun specFor(model: String?): Spec? {
        val m = model?.lowercase()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return TABLE.firstOrNull { (key, _) -> key.trim() in m }?.second
    }

    fun nominalHashrateGhs(model: String?): Double? = specFor(model)?.nominalHashrateGhs
    fun nominalPowerW(model: String?): Double? = specFor(model)?.nominalPowerW
}
