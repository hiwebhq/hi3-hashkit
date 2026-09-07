package hi3.hashkit.data.repo

import hi3.hashkit.data.db.TelemetrySampleEntity
import hi3.hashkit.data.db.TelemetryHourlyEntity

/** Pure aggregation of raw samples into hourly rows — fully unit-testable. */
object Downsampler {

    const val HOUR_MS = 3_600_000L

    /** Cap inter-sample gaps when integrating energy, matching the live-stats rule. */
    private const val MAX_GAP_MS = 300_000L

    fun hourStartOf(timestampMs: Long): Long = timestampMs - timestampMs % HOUR_MS

    /**
     * Aggregate raw [samples] (any order, possibly spanning hours) into hourly rows.
     * Hours with no samples produce no row — absence stays honest.
     */
    fun aggregate(minerId: Long, samples: List<TelemetrySampleEntity>): List<TelemetryHourlyEntity> =
        samples
            .sortedBy { it.timestampEpochMs }
            .groupBy { hourStartOf(it.timestampEpochMs) }
            .map { (hourStart, rows) ->
                val hashrates = rows.mapNotNull { it.hashrateGhs }
                val powers = rows.mapNotNull { it.powerW }
                val chipTemps = rows.mapNotNull { it.chipTempC }
                var energyWh = 0.0
                var hasEnergy = false
                for (i in 1 until rows.size) {
                    val dtH = (rows[i].timestampEpochMs - rows[i - 1].timestampEpochMs)
                        .coerceAtMost(MAX_GAP_MS) / 3_600_000.0
                    rows[i].powerW?.let { energyWh += it * dtH; hasEnergy = true }
                }
                TelemetryHourlyEntity(
                    minerId = minerId,
                    hourStartEpochMs = hourStart,
                    samples = rows.size,
                    onlineSamples = rows.count { it.status == "ONLINE" },
                    avgHashrateGhs = hashrates.average().takeIf { hashrates.isNotEmpty() },
                    minHashrateGhs = hashrates.minOrNull(),
                    maxHashrateGhs = hashrates.maxOrNull(),
                    avgPowerW = powers.average().takeIf { powers.isNotEmpty() },
                    avgChipTempC = chipTemps.average().takeIf { chipTemps.isNotEmpty() },
                    maxChipTempC = chipTemps.maxOrNull(),
                    maxVrTempC = rows.mapNotNull { it.vrTempC }.maxOrNull(),
                    energyWh = energyWh.takeIf { hasEnergy },
                )
            }
            .sortedBy { it.hourStartEpochMs }
}
