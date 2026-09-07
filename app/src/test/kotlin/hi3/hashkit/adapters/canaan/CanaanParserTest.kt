package hi3.hashkit.adapters.canaan

import hi3.hashkit.domain.model.ValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures captured from a real Avalon Nano 3 (fw 24071801) and redacted. */
class CanaanParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/canaan/$name"))
            .bufferedReader().readText()

    @Test
    fun `version identifies the nano3`() {
        val v = CanaanParser.parseVersion(fixture("nano3_version.json"))
        assertNotNull(v)
        assertTrue(CanaanParser.isAvalon(v))
        assertEquals("nano3", v!!.model)
        assertEquals("24071801_42c628d", v.firmwareVersion)
    }

    @Test
    fun `non-avalon responses are rejected`() {
        assertFalse(CanaanParser.isAvalon(CanaanParser.parseVersion("""{"VERSION":[{"PROD":"Antminer"}]}""")))
        assertFalse(CanaanParser.isAvalon(CanaanParser.parseVersion("{}")))
        assertFalse(CanaanParser.isAvalon(CanaanParser.parseVersion("not json")))
        assertNull(CanaanParser.parseVersion("""{"STATUS":[]}"""))
    }

    @Test
    fun `identity uses hardware dna as serial and maps model names`() {
        val v = CanaanParser.parseVersion(fixture("nano3_version.json"))!!
        val mm = CanaanParser.mmFieldsOf(fixture("nano3_estats.json"))
        val id = CanaanParser.identityOf(v, mm)
        assertEquals("Canaan", id.manufacturer)
        assertEquals("Avalon Nano 3", id.model)
        // DNA is all zeros on this firmware; HDNA is used instead.
        assertEquals("0201000000000001", id.serialNumber)
        assertEquals("A3198S", id.asicModel)
        assertTrue(id.stableKey("1.2.3.4").startsWith("sn:"))
    }

    @Test
    fun `telemetry maps summary and estats to canonical units`() {
        val t = CanaanParser.parseTelemetry(
            fixture("nano3_summary.json"),
            fixture("nano3_estats.json"),
            fixture("nano3_pools.json"),
            fixture("nano3_coin.json"),
        )
        // MHS 5m = 3500849.21 -> 3500.85 GH/s
        assertEquals(3500.85, t.hashrateGhs.value!!, 0.01)
        assertEquals(ValueSource.REPORTED, t.hashrateGhs.source)
        // PS[5] = 124 W, reported not measured (sensor chain undocumented)
        assertEquals(124.0, t.powerW.value!!, 0.001)
        assertEquals(ValueSource.REPORTED, t.powerW.source)
        // TMax = 92 (hottest chip)
        assertEquals(92.0, t.chipTempC.value!!, 0.001)
        assertEquals(ValueSource.UNAVAILABLE, t.vrTempC.source)
        // Fan1 4740 RPM at 59%
        assertEquals(4740, t.fans.single().rpm)
        assertEquals(59, t.fans.single().percent)
        assertEquals(10, t.asicCount)
        assertEquals(180262L, t.sharesAccepted)
        assertEquals(605L, t.sharesRejected)
        assertEquals(682946194.0, t.bestDifficulty!!, 1.0)
        assertEquals(1226068L, t.uptimeSeconds)
        assertNotNull(t.efficiencyJTh.value)
    }

    @Test
    fun `active pool and network difficulty are extracted`() {
        val t = CanaanParser.parseTelemetry(
            fixture("nano3_summary.json"),
            fixture("nano3_estats.json"),
            fixture("nano3_pools.json"),
            fixture("nano3_coin.json"),
        )
        assertEquals("192.0.2.10", t.poolUrl)
        assertEquals(3333, t.poolPort)
        assertEquals("bc1qredactedexampleaddress.0x51", t.workerName)
        assertEquals(false, t.usingFallbackPool)
        assertEquals(1.2745078971584314e14, t.networkDifficulty!!, 1e6)
    }

    @Test
    fun `partial responses degrade gracefully`() {
        // estats only: hashrate falls back to GHSavg, no shares.
        val estatsOnly = CanaanParser.parseTelemetry(null, fixture("nano3_estats.json"), null, null)
        assertEquals(3292.01, estatsOnly.hashrateGhs.value!!, 0.01)
        assertNull(estatsOnly.sharesAccepted)
        assertNull(estatsOnly.networkDifficulty)

        // summary only: no power/temp/fan, nothing invented.
        val summaryOnly = CanaanParser.parseTelemetry(fixture("nano3_summary.json"), null, null, null)
        assertEquals(ValueSource.UNAVAILABLE, summaryOnly.powerW.source)
        assertTrue(summaryOnly.fans.isEmpty())
        assertNull(summaryOnly.chipTempC.value)
    }

    @Test
    fun `unconsumed mm fields are preserved for diagnostics`() {
        val t = CanaanParser.parseTelemetry(null, fixture("nano3_estats.json"), null, null)
        assertTrue("WORKLEVEL" in t.unrecognizedFields)
        assertTrue("MPO" in t.unrecognizedFields)
        assertEquals("2", t.unrecognizedFields["WORKLEVEL"])
    }

    @Test
    fun `malformed bodies do not crash`() {
        val t = CanaanParser.parseTelemetry("garbage", "{]", "", null)
        assertNull(t.hashrateGhs.value)
        assertTrue(CanaanParser.mmFieldsOf("nonsense").isEmpty())
        assertTrue(CanaanParser.mmFieldsOf(null).isEmpty())
    }
}
