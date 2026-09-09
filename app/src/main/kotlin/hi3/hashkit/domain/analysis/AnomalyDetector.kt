package hi3.hashkit.domain.analysis

import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry

/**
 * Statistical anomaly detection over a miner's recent history. Complements the fixed
 * threshold alerts by catching *gradual* trends a static limit misses — e.g. a hashrate
 * slowly sliding down over hours while still above the absolute floor, or a reject rate
 * creeping up.
 *
 * Pure and deterministic (no clock, no Android) so it is straightforward to unit-test.
 * Input history is oldest→newest, as stored.
 */
object AnomalyDetector {

    enum class Type { HASHRATE_DRIFT, REJECT_RATE_RISING }

    data class Finding(
        val type: Type,
        /** Human-readable summary, e.g. "Hashrate down 14% vs the earlier baseline". */
        val message: String,
        /** Magnitude 0..1 (fraction drop, or reject-rate delta) for ranking/thresholds. */
        val magnitude: Double,
    )

    /** Default: recent = newest third of the window; baseline = the older two thirds. */
    fun analyze(
        history: List<MinerTelemetry>,
        driftThreshold: Double = 0.10,
        rejectDeltaThreshold: Double = 0.01,
        minSamplesPerSide: Int = 4,
    ): List<Finding> {
        val online = history.filter {
            it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED
        }
        if (online.size < minSamplesPerSide * 2) return emptyList()

        val split = online.size * 2 / 3
        val baseline = online.subList(0, split)
        val recent = online.subList(split, online.size)
        if (baseline.size < minSamplesPerSide || recent.size < minSamplesPerSide) return emptyList()

        val findings = mutableListOf<Finding>()

        // --- Hashrate drift: recent mean vs baseline mean ---
        val baseHr = baseline.mapNotNull { it.hashrateGhs.value }.filter { it > 0 }
        val recentHr = recent.mapNotNull { it.hashrateGhs.value }.filter { it > 0 }
        if (baseHr.size >= minSamplesPerSide && recentHr.size >= minSamplesPerSide) {
            val b = baseHr.average()
            val r = recentHr.average()
            if (b > 0) {
                val drop = (b - r) / b
                if (drop >= driftThreshold) {
                    findings += Finding(
                        Type.HASHRATE_DRIFT,
                        "Hashrate down ${(drop * 100).toInt()}% vs the earlier baseline " +
                            "(${fmt(r)} vs ${fmt(b)} GH/s avg)",
                        drop,
                    )
                }
            }
        }

        // --- Reject-rate rising: windowed reject ratio from cumulative counters ---
        val baseReject = windowedRejectRatio(baseline)
        val recentReject = windowedRejectRatio(recent)
        if (baseReject != null && recentReject != null) {
            val delta = recentReject - baseReject
            if (delta >= rejectDeltaThreshold && recentReject >= 0.02) {
                findings += Finding(
                    Type.REJECT_RATE_RISING,
                    "Reject rate rising: ${pct(recentReject)} recently vs ${pct(baseReject)} earlier",
                    delta,
                )
            }
        }

        return findings
    }

    /**
     * Reject ratio across a window, from the cumulative accepted/rejected counters at its
     * ends (delta), so it reflects the window rather than lifetime totals. Null if the
     * counters aren't present or didn't advance (e.g. a counter reset).
     */
    private fun windowedRejectRatio(window: List<MinerTelemetry>): Double? {
        val first = window.firstOrNull { it.sharesAccepted != null && it.sharesRejected != null } ?: return null
        val last = window.lastOrNull { it.sharesAccepted != null && it.sharesRejected != null } ?: return null
        val dAcc = (last.sharesAccepted ?: 0) - (first.sharesAccepted ?: 0)
        val dRej = (last.sharesRejected ?: 0) - (first.sharesRejected ?: 0)
        if (dAcc < 0 || dRej < 0) return null // counter reset
        val total = dAcc + dRej
        if (total <= 0) return null
        return dRej.toDouble() / total
    }

    private fun fmt(v: Double) = "%.0f".format(v)
    private fun pct(v: Double) = "%.1f%%".format(v * 100)
}
