package hi3.hashkit.data.repo

import hi3.hashkit.data.db.FleetSamplePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetSeriesTest {

    private val start = 1_000_000L
    private val end = start + 60_000L // 60s window

    @Test
    fun `sums latest sample per miner within each bucket`() {
        // 2 miners, one bucket: each miner's LATEST sample counts once.
        val pts = listOf(
            FleetSamplePoint(1, start + 1_000, 100.0),
            FleetSamplePoint(1, start + 2_000, 110.0), // newer for miner 1 -> wins
            FleetSamplePoint(2, start + 1_500, 200.0),
        )
        val series = FleetSeries.bucket(pts, start, end, buckets = 1)
        assertEquals(1, series.size)
        assertEquals(310.0, series[0].totalGhs, 0.001) // 110 + 200, not 100+110+200
    }

    @Test
    fun `separate buckets track the fleet total over time`() {
        val pts = listOf(
            FleetSamplePoint(1, start + 5_000, 100.0),
            FleetSamplePoint(2, start + 5_000, 100.0),
            FleetSamplePoint(1, start + 35_000, 150.0),
            FleetSamplePoint(2, start + 35_000, 150.0),
        )
        val series = FleetSeries.bucket(pts, start, end, buckets = 2)
        assertEquals(2, series.size)
        assertEquals(200.0, series[0].totalGhs, 0.001)
        assertEquals(300.0, series[1].totalGhs, 0.001)
        assertTrue(series[0].timeMs < series[1].timeMs)
    }

    @Test
    fun `null hashrates and out-of-window points are ignored, empty buckets omitted`() {
        val pts = listOf(
            FleetSamplePoint(1, start + 1_000, null),      // null -> ignored
            FleetSamplePoint(1, start - 5_000, 999.0),      // before window -> ignored
            FleetSamplePoint(2, start + 40_000, 50.0),      // only this counts
        )
        val series = FleetSeries.bucket(pts, start, end, buckets = 4)
        assertEquals(1, series.size)
        assertEquals(50.0, series[0].totalGhs, 0.001)
    }

    @Test
    fun `empty input yields empty series`() {
        assertTrue(FleetSeries.bucket(emptyList(), start, end).isEmpty())
    }
}
