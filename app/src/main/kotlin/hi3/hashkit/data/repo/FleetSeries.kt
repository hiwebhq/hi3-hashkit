package hi3.hashkit.data.repo

import hi3.hashkit.data.db.FleetSamplePoint

/** A fleet-total hashrate point (bucket-center time, summed GH/s across miners). */
data class FleetTrendPoint(val timeMs: Long, val totalGhs: Double)

/**
 * Buckets per-miner samples into a fleet-total time series. Within each time bucket,
 * each miner contributes its latest sample in that bucket (so a miner polled twice in
 * a bucket isn't double-counted), and the contributions are summed. Empty buckets
 * produce no point, so gaps in coverage stay gaps rather than false zeros.
 */
object FleetSeries {

    fun bucket(
        points: List<FleetSamplePoint>,
        startMs: Long,
        endMs: Long,
        buckets: Int = 60,
    ): List<FleetTrendPoint> {
        if (points.isEmpty() || endMs <= startMs || buckets < 1) return emptyList()
        val span = endMs - startMs
        val bucketMs = (span / buckets).coerceAtLeast(1)
        // bucketIndex -> (minerId -> latest (ts, hashrate) in that bucket)
        val byBucket = HashMap<Int, HashMap<Long, Pair<Long, Double>>>()
        for (p in points) {
            val hr = p.hashrateGhs ?: continue
            if (p.timestampEpochMs < startMs || p.timestampEpochMs > endMs) continue
            val idx = ((p.timestampEpochMs - startMs) / bucketMs).toInt().coerceIn(0, buckets - 1)
            val minerMap = byBucket.getOrPut(idx) { HashMap() }
            val prev = minerMap[p.minerId]
            if (prev == null || p.timestampEpochMs >= prev.first) {
                minerMap[p.minerId] = p.timestampEpochMs to hr
            }
        }
        return byBucket.entries
            .sortedBy { it.key }
            .map { (idx, miners) ->
                FleetTrendPoint(
                    timeMs = startMs + idx * bucketMs + bucketMs / 2,
                    totalGhs = miners.values.sumOf { it.second },
                )
            }
    }
}
