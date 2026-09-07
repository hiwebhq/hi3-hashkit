package hi3.hashkit.data.repo

import hi3.hashkit.data.db.TelemetrySampleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownsamplerTest {

    private val hour0 = 1_800_000_000_000L - 1_800_000_000_000L % Downsampler.HOUR_MS

    private fun sample(
        offsetMs: Long,
        hashrate: Double? = 1000.0,
        power: Double? = 15.0,
        chipTemp: Double? = 60.0,
        vrTemp: Double? = null,
        status: String = "ONLINE",
    ) = TelemetrySampleEntity(
        minerId = 1, timestampEpochMs = hour0 + offsetMs, status = status,
        hashrateGhs = hashrate, hashrateSource = "REPORTED", expectedHashrateGhs = null,
        powerW = power, powerSource = "MEASURED", efficiencyJTh = null,
        chipTempC = chipTemp, vrTempC = vrTemp, fansJson = "[]",
        frequencyMhz = null, coreVoltageMv = null, inputVoltageMv = null, asicCount = null,
        sharesAccepted = null, sharesRejected = null, bestDifficulty = null,
        bestSessionDifficulty = null, uptimeSeconds = null, networkDifficulty = null,
        poolUrl = null, poolPort = null, workerName = null, usingFallbackPool = null,
    )

    @Test
    fun `aggregates one hour with avg min max and energy`() {
        // Four samples 15 min apart: 45 min of integration at varying power.
        val rows = Downsampler.aggregate(1, listOf(
            sample(0, hashrate = 900.0, power = 12.0, chipTemp = 55.0),
            sample(900_000, hashrate = 1000.0, power = 16.0, chipTemp = 60.0),
            sample(1_800_000, hashrate = 1100.0, power = 16.0, chipTemp = 65.0),
            sample(2_700_000, hashrate = 1000.0, power = 16.0, chipTemp = 62.0, vrTemp = 70.0),
        ))
        val h = rows.single()
        assertEquals(hour0, h.hourStartEpochMs)
        assertEquals(4, h.samples)
        assertEquals(4, h.onlineSamples)
        assertEquals(1000.0, h.avgHashrateGhs!!, 0.001)
        assertEquals(900.0, h.minHashrateGhs!!, 0.001)
        assertEquals(1100.0, h.maxHashrateGhs!!, 0.001)
        assertEquals(65.0, h.maxChipTempC!!, 0.001)
        assertEquals(70.0, h.maxVrTempC!!, 0.001)
        // Gaps are 15 min each but capped at 5 min: 3 intervals * (5/60)h * 16W = 4 Wh.
        assertEquals(4.0, h.energyWh!!, 0.001)
    }

    @Test
    fun `splits samples across hour boundaries`() {
        val rows = Downsampler.aggregate(1, listOf(
            sample(3_500_000),                       // hour 0
            sample(Downsampler.HOUR_MS + 100_000),   // hour 1
            sample(Downsampler.HOUR_MS + 200_000),   // hour 1
        ))
        assertEquals(2, rows.size)
        assertEquals(1, rows[0].samples)
        assertEquals(2, rows[1].samples)
        assertEquals(hour0 + Downsampler.HOUR_MS, rows[1].hourStartEpochMs)
    }

    @Test
    fun `offline samples count toward totals but not online`() {
        val h = Downsampler.aggregate(1, listOf(
            sample(0),
            sample(60_000, hashrate = null, power = null, chipTemp = null, status = "OFFLINE"),
        )).single()
        assertEquals(2, h.samples)
        assertEquals(1, h.onlineSamples)
        // Nulls never pollute the aggregates.
        assertEquals(1000.0, h.avgHashrateGhs!!, 0.001)
    }

    @Test
    fun `all-null metrics produce null aggregates not zeros`() {
        val h = Downsampler.aggregate(1, listOf(
            sample(0, hashrate = null, power = null, chipTemp = null, status = "OFFLINE"),
            sample(60_000, hashrate = null, power = null, chipTemp = null, status = "OFFLINE"),
        )).single()
        assertNull(h.avgHashrateGhs)
        assertNull(h.avgPowerW)
        assertNull(h.energyWh)
        assertEquals(0, h.onlineSamples)
    }

    @Test
    fun `empty input produces no rows and unsorted input is handled`() {
        assertTrue(Downsampler.aggregate(1, emptyList()).isEmpty())
        val rows = Downsampler.aggregate(1, listOf(sample(500_000), sample(100_000)))
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].samples)
    }
}
