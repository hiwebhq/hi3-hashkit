package hi3.hashkit.domain.model

import java.time.Instant

data class FanReading(
    val index: Int,
    val rpm: Int?,
    val percent: Int?,
)

/**
 * Per-board/per-chain health, for miners whose firmware reports it (Antminer-class
 * `stats`: chain_rateN / chain_acnN / chain_acsN / chain_hwN). Absent boards (no chips
 * detected) are omitted by the adapter. [chipsDead] > 0 or a chain far below its siblings
 * flags a failing board before it drags down the fleet total.
 */
data class ChainReading(
    val index: Int,
    val hashrateGhs: Double?,
    /** Chips the firmware reports active on this chain (chain_acnN). */
    val chipsActive: Int?,
    /** Total chip slots seen in the status string (o + x), when available. */
    val chipsTotal: Int?,
    /** Chips flagged failed in the status string ('x'); 0 = all good. */
    val chipsDead: Int?,
    /** Hardware-error counter for the chain (chain_hwN), if reported. */
    val hwErrors: Int?,
    /** Hottest chip temperature on this chain, °C. */
    val tempC: Double?,
)

/**
 * Normalized telemetry snapshot. Internal canonical units:
 * hashrate GH/s, power W, temperature °C, voltage mV, frequency MHz, efficiency J/TH.
 */
data class MinerTelemetry(
    val timestamp: Instant,
    val status: MinerStatus,

    val hashrateGhs: Sourced<Double> = Sourced.unavailable(),
    val expectedHashrateGhs: Sourced<Double> = Sourced.unavailable(),

    val powerW: Sourced<Double> = Sourced.unavailable(),
    val efficiencyJTh: Sourced<Double> = Sourced.unavailable(),

    val chipTempC: Sourced<Double> = Sourced.unavailable(),
    val vrTempC: Sourced<Double> = Sourced.unavailable(),

    val fans: List<FanReading> = emptyList(),

    /** Per-chain/per-board health where the firmware reports it; empty otherwise. */
    val perChain: List<ChainReading> = emptyList(),

    /** Whether firmware-managed fan control is on. Not persisted; null when unknown. */
    val autoFanEnabled: Boolean? = null,

    val frequencyMhz: Sourced<Double> = Sourced.unavailable(),
    val coreVoltageMv: Sourced<Double> = Sourced.unavailable(),
    val inputVoltageMv: Sourced<Double> = Sourced.unavailable(),

    val asicCount: Int? = null,
    val sharesAccepted: Long? = null,
    val sharesRejected: Long? = null,
    val bestDifficulty: Double? = null,
    val bestSessionDifficulty: Double? = null,

    val uptimeSeconds: Long? = null,
    /** Bitcoin network difficulty when the miner itself reports it (e.g. Canaan `coin`). */
    val networkDifficulty: Double? = null,
    val poolUrl: String? = null,
    val poolPort: Int? = null,
    val workerName: String? = null,
    val usingFallbackPool: Boolean? = null,

    /** Fields the parser did not recognize, preserved verbatim for diagnostics. */
    val unrecognizedFields: Map<String, String> = emptyMap(),
) {
    /** Hashrate attainment vs expected, as a percentage, when both are known. */
    val attainmentPercent: Double?
        get() {
            val actual = hashrateGhs.value ?: return null
            val expected = expectedHashrateGhs.value ?: return null
            if (expected <= 0.0) return null
            return actual / expected * 100.0
        }
}
