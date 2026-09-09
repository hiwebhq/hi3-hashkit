package hi3.hashkit.domain.analysis

import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AnomalyDetectorTest {

    private fun sample(hr: Double, acc: Long? = null, rej: Long? = null, t: Long = 0): MinerTelemetry =
        MinerTelemetry(
            timestamp = Instant.ofEpochMilli(t),
            status = MinerStatus.ONLINE,
            hashrateGhs = Sourced.reported(hr),
            sharesAccepted = acc,
            sharesRejected = rej,
        )

    @Test fun steadyHashrateProducesNoFindings() {
        val history = (1..18).map { sample(1000.0, t = it.toLong()) }
        assertTrue(AnomalyDetector.analyze(history).isEmpty())
    }

    @Test fun gradualHashrateDropIsFlagged() {
        // Baseline ~1000, recent third ~800 -> ~20% drop.
        val baseline = (1..12).map { sample(1000.0, t = it.toLong()) }
        val recent = (13..18).map { sample(800.0, t = it.toLong()) }
        val findings = AnomalyDetector.analyze(baseline + recent)
        val drift = findings.firstOrNull { it.type == AnomalyDetector.Type.HASHRATE_DRIFT }
        assertTrue("expected a drift finding", drift != null)
        assertEquals(0.20, drift!!.magnitude, 0.03)
    }

    @Test fun tooFewSamplesYieldsNothing() {
        assertTrue(AnomalyDetector.analyze(listOf(sample(1000.0), sample(500.0))).isEmpty())
    }

    @Test fun risingRejectRateIsFlagged() {
        // Cumulative counters: baseline window adds ~0.5% rejects, recent window ~5%.
        val history = buildList {
            var acc = 0L; var rej = 0L
            // baseline: 12 samples, +100 acc +1 rej each
            repeat(12) { add(sample(1000.0, acc, rej, it.toLong())); acc += 100; rej += 1 }
            // recent: 6 samples, +100 acc +5 rej each
            repeat(6) { add(sample(1000.0, acc, rej, (12 + it).toLong())); acc += 100; rej += 5 }
        }
        val findings = AnomalyDetector.analyze(history)
        assertTrue(
            "expected rising-reject finding",
            findings.any { it.type == AnomalyDetector.Type.REJECT_RATE_RISING },
        )
    }

    @Test fun offlineSamplesAreExcluded() {
        val history = (1..18).map {
            sample(1000.0, t = it.toLong()).copy(status = MinerStatus.OFFLINE)
        }
        assertTrue(AnomalyDetector.analyze(history).isEmpty())
    }
}
