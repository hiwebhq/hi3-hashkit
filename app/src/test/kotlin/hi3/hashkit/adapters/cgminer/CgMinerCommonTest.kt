package hi3.hashkit.adapters.cgminer

import hi3.hashkit.domain.model.ValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CgMinerCommonTest {

    private fun fx(path: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)).bufferedReader().readText()

    // --- family detection --------------------------------------------------------------

    @Test
    fun `detects antminer vnish luxos from version records`() {
        assertEquals(CgMinerCommon.Family.ANTMINER_STOCK,
            CgMinerCommon.family(fx("fixtures/cgminer/antminer_stock_version.json")))
        assertEquals(CgMinerCommon.Family.VNISH,
            CgMinerCommon.family(fx("fixtures/cgminer/vnish_version.json")))
        assertEquals(CgMinerCommon.Family.LUXOS,
            CgMinerCommon.family(fx("fixtures/cgminer/luxos_version.json")))
    }

    @Test
    fun `declines avalon and boser using REAL device captures`() {
        // The generic adapter must never claim these — they have specific adapters.
        assertEquals(CgMinerCommon.Family.AVALON,
            CgMinerCommon.family(fx("fixtures/canaan/nano3_version.json")))
        assertEquals(CgMinerCommon.Family.BOSER,
            CgMinerCommon.family(fx("fixtures/braiins/bmm100_version.json")))
    }

    @Test
    fun `non-cgminer input yields null family`() {
        assertNull(CgMinerCommon.family("not json"))
        assertNull(CgMinerCommon.family("{}"))
    }

    // --- standard telemetry (MHS -> GH/s, shares, uptime, pool) -------------------------

    @Test
    fun `parses standard summary and pools`() {
        val t = CgMinerCommon.parseStandardTelemetry(
            fx("fixtures/cgminer/std_summary.json"),
            fx("fixtures/cgminer/std_pools.json"),
        )
        // MHS 5s 96,000,000 MH/s -> 96,000 GH/s (96 TH/s)
        assertEquals(96_000.0, t.hashrateGhs.value!!, 0.001)
        assertEquals(ValueSource.REPORTED, t.hashrateGhs.source)
        assertEquals(10_000L, t.sharesAccepted)
        assertEquals(42L, t.sharesRejected)
        assertEquals(86_400L, t.uptimeSeconds)
        assertEquals(123456789.0, t.bestDifficulty!!, 1.0)
        assertEquals("192.0.2.10", t.poolUrl)
        assertEquals(3333, t.poolPort)
        // Firmware-specific data stays unavailable — never invented.
        assertEquals(ValueSource.UNAVAILABLE, t.powerW.source)
        assertNull(t.chipTempC.value)
        assertEquals(0, t.fans.size)
    }

    @Test
    fun `Bitmain S21 - GHS summary and stats temps fans (real capture)`() {
        // Verified against a real Antminer S21 Pro (BMMiner 1.0.0).
        assertEquals(CgMinerCommon.Family.ANTMINER_STOCK,
            CgMinerCommon.family(fx("fixtures/cgminer/antminer_s21_version.json")))
        val base = CgMinerCommon.parseStandardTelemetry(
            fx("fixtures/cgminer/antminer_s21_summary.json"),
            fx("fixtures/cgminer/antminer_s21_pools.json"),
        )
        // Summary reports GHS 5s = 251116.18 GH/s (not MHS) -> used directly.
        assertEquals(251116.18, base.hashrateGhs.value!!, 0.1)
        assertEquals(86330L, base.sharesAccepted)
        assertEquals("192.0.2.10", base.poolUrl)

        val t = CgMinerCommon.enrichWithStats(base, fx("fixtures/cgminer/antminer_s21_stats.json"))
        // Hottest chip across temp2_* (71,70,72) and temp_chip strings = 72.
        assertEquals(72.0, t.chipTempC.value!!, 0.001)
        assertEquals(ValueSource.MEASURED, t.chipTempC.source)
        assertEquals(4, t.fans.size)
        assertEquals(6570, t.fans[0].rpm)
        assertEquals(245000.0, t.expectedHashrateGhs.value!!, 0.1) // total_rateideal
        assertEquals(625.0, t.frequencyMhz.value!!, 0.1)
        assertEquals(195, t.asicCount)
        // Power is not in the Bitmain cgminer API -> honestly unavailable.
        assertEquals(ValueSource.UNAVAILABLE, t.powerW.source)
        // Board temps (temp1/2/3 = 66/65/67) preserved for diagnostics; max = 67.
        assertEquals(67.0, t.unrecognizedFields["boardTempC"]!!.toDouble(), 0.001)

        // Per-chain health: 3 populated boards (chain 4 is absent: acn 0, blank rate/acs).
        assertEquals(3, t.perChain.size)
        val chain1 = t.perChain.first { it.index == 1 }
        assertEquals(83852.84, chain1.hashrateGhs!!, 0.01) // chain_rate1
        assertEquals(65, chain1.chipsActive)               // chain_acn1
        assertEquals(65, chain1.chipsTotal)                // chain_acs1: 65 'o's, no spaces counted
        assertEquals(0, chain1.chipsDead)                  // no 'x'
        assertEquals(100, chain1.hwErrors)                 // chain_hw1
    }

    @Test
    fun `VNish S21 - detected from stats, temps fans and wall power (real capture)`() {
        // VNish's `version` errors; detection falls back to the stats Type field.
        assertEquals(CgMinerCommon.Family.VNISH,
            CgMinerCommon.familyFromStats(fx("fixtures/cgminer/vnish_s21_stats.json")))
        val base = CgMinerCommon.parseStandardTelemetry(
            fx("fixtures/cgminer/vnish_s21_summary.json"),
            fx("fixtures/cgminer/vnish_s21_pools.json"),
        )
        val t = CgMinerCommon.enrichWithStats(base, fx("fixtures/cgminer/vnish_s21_stats.json"))
        // GHS 5s = 162986.98 (a string in VNish stats) parsed to GH/s.
        assertEquals(162986.98, t.hashrateGhs.value!!, 0.1)
        // Chip temp max across temp2_*/temp3_*/temp_chip* = 67.
        assertEquals(67.0, t.chipTempC.value!!, 0.001)
        assertEquals(4, t.fans.size)
        assertEquals(6390, t.fans[0].rpm)
        // VNish DOES report wall power via chain_consumption (1114*3 = 3342 W).
        assertEquals(3342.0, t.powerW.value!!, 0.1)
        assertEquals(ValueSource.REPORTED, t.powerW.source)
        assertEquals(236691.0, t.expectedHashrateGhs.value!!, 0.1)
        assertEquals(595.0, t.frequencyMhz.value!!, 0.1) // avg of freq_avg*
        assertEquals(195, t.asicCount)
        assertNotNull(t.efficiencyJTh.value) // now computable — power is present
    }

    @Test
    fun `WhatsMiner - detected from btminer status, standard telemetry plus temps fans power`() {
        // Shape per MicroBT's documented API (btminer summary); pending on-device verification.
        assertEquals("btminer 2.0.5",
            CgMinerCommon.firstStatusDescription(fx("fixtures/cgminer/whatsminer_summary.json")))
        val base = CgMinerCommon.parseStandardTelemetry(
            fx("fixtures/cgminer/whatsminer_summary.json"),
            fx("fixtures/cgminer/whatsminer_pools.json"),
        )
        // MHS 5s 86,000,000 -> 86,000 GH/s.
        assertEquals(86_000.0, base.hashrateGhs.value!!, 0.1)
        assertEquals(123_456L, base.uptimeSeconds)
        assertEquals("192.0.2.10", base.poolUrl)

        val t = CgMinerCommon.enrichWhatsMiner(base, fx("fixtures/cgminer/whatsminer_summary.json"))
        assertEquals(72.0, t.chipTempC.value!!, 0.001)
        assertEquals(ValueSource.MEASURED, t.chipTempC.source)
        assertEquals(2, t.fans.size)
        assertEquals(3900, t.fans[0].rpm)
        assertEquals(3400.0, t.powerW.value!!, 0.1)
        assertEquals(ValueSource.REPORTED, t.powerW.source)
        assertNotNull(t.efficiencyJTh.value) // power present -> J/TH computed
        assertEquals("28.0", t.unrecognizedFields["envTempC"])
    }

    @Test
    fun `standard parse of a REAL cgminer summary (Avalon Nano 3 capture)`() {
        // Confirms the standard-field extraction works on a genuine cgminer response.
        val t = CgMinerCommon.parseStandardTelemetry(
            fx("fixtures/canaan/nano3_summary.json"),
            fx("fixtures/canaan/nano3_pools.json"),
        )
        // Standard extraction uses "MHS 5s" (2853563.24 MH/s -> 2853.56 GH/s).
        assertEquals(2853.56, t.hashrateGhs.value!!, 0.1)
    }
}
