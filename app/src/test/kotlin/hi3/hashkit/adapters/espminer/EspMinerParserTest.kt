package hi3.hashkit.adapters.espminer

import hi3.hashkit.domain.model.ValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EspMinerParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/espminer/$name")) {
            "Missing fixture $name"
        }.bufferedReader().readText()

    // --- fixtures recorded from real devices on the user's network (redacted) ---------

    @Test
    fun `parses real BM1366 v2_15_1 response`() {
        val parsed = EspMinerParser.parseSystemInfo(fixture("real_bm1366_v2.15.1.json"))
        assertNotNull(parsed)
        parsed!!
        assertEquals("BM1366", parsed.identity.asicModel)
        assertEquals("v2.15.1", parsed.identity.firmwareVersion)
        assertEquals(521.7946777, parsed.telemetry.hashrateGhs.value!!, 0.001)
        assertEquals(14.6800003, parsed.telemetry.powerW.value!!, 0.001)
        assertEquals(ValueSource.MEASURED, parsed.telemetry.powerW.source)
        assertEquals(447.0, parsed.telemetry.expectedHashrateGhs.value!!, 0.001)
        // vrTemp of 0 is reported as-is; interpretation is a health-score concern.
        assertEquals(0.0, parsed.telemetry.vrTempC.value!!, 0.001)
        assertNotNull(parsed.telemetry.efficiencyJTh.value)
        assertEquals(ValueSource.CALCULATED, parsed.telemetry.efficiencyJTh.source)
    }

    @Test
    fun `parses real BM1370 v2_14_2 response`() {
        val parsed = EspMinerParser.parseSystemInfo(fixture("real_bm1370_v2.14.2.json"))!!
        assertEquals(1232.6506348, parsed.telemetry.hashrateGhs.value!!, 0.001)
        assertEquals(1292142363.0, parsed.telemetry.bestDifficulty!!, 1.0)
        assertEquals("02:00:00:00:00:02", parsed.identity.macAddress)
    }

    @Test
    fun `parses fork firmware with string bestDiff and missing fields`() {
        val parsed = EspMinerParser.parseSystemInfo(fixture("real_variant_2.0.0_string_bestdiff.json"))!!
        // "7.64G" -> 7.64e9
        assertEquals(7.64e9, parsed.telemetry.bestDifficulty!!, 1e7)
        // vrTemp is null on this firmware -> UNAVAILABLE, never zero-filled
        assertNull(parsed.telemetry.vrTempC.value)
        assertEquals(ValueSource.UNAVAILABLE, parsed.telemetry.vrTempC.source)
        assertNull(parsed.telemetry.expectedHashrateGhs.value)
        // Fork reports a serial number, which becomes the stable identity
        assertNotNull(parsed.identity.serialNumber)
        assertTrue(parsed.identity.stableKey("1.2.3.4").startsWith("mac:"))
    }

    @Test
    fun `parses old v1_1_0 firmware`() {
        val parsed = EspMinerParser.parseSystemInfo(fixture("real_bm1370_v1.1.0.json"))!!
        assertEquals("v1.1.0", parsed.identity.firmwareVersion)
        assertEquals(6057.933, parsed.telemetry.hashrateGhs.value!!, 0.001)
    }

    @Test
    fun `parses dual-chip board and preserves unrecognized fields`() {
        val parsed = EspMinerParser.parseSystemInfo(fixture("real_bm1372_duo_v2.14.0.json"))!!
        assertEquals("BM1372/BM1373", parsed.identity.asicModel)
        // Real firmware carries many fields the app doesn't consume yet; they must be
        // preserved for diagnostics, not dropped.
        assertTrue(parsed.telemetry.unrecognizedFields.isNotEmpty())
    }

    // --- synthetic edge cases ---------------------------------------------------------

    @Test
    fun `handles numbers encoded as strings and null values`() {
        val parsed = EspMinerParser.parseSystemInfo(fixture("synthetic_strings_and_nulls.json"))!!
        assertEquals(498.75, parsed.telemetry.hashrateGhs.value!!, 0.001)
        assertEquals(13.9, parsed.telemetry.powerW.value!!, 0.001)
        assertEquals(4.29e6, parsed.telemetry.bestDifficulty!!, 1.0)
        assertEquals(112_000.0, parsed.telemetry.bestSessionDifficulty!!, 1.0)
        assertEquals(1041L, parsed.telemetry.sharesAccepted)
        assertNull(parsed.telemetry.vrTempC.value)
        assertNull(parsed.telemetry.inputVoltageMv.value)
        assertTrue("someFutureField" in parsed.telemetry.unrecognizedFields)
    }

    @Test
    fun `handles minimal response without inventing values`() {
        val parsed = EspMinerParser.parseSystemInfo(fixture("synthetic_minimal.json"))!!
        assertEquals(512.0, parsed.telemetry.hashrateGhs.value!!, 0.001)
        assertEquals(ValueSource.UNAVAILABLE, parsed.telemetry.powerW.source)
        assertNull(parsed.telemetry.powerW.value)
        assertNull(parsed.telemetry.chipTempC.value)
        // No MAC/serial -> identity falls back to IP-based stable key
        assertEquals("ip:10.0.0.5", parsed.identity.stableKey("10.0.0.5"))
    }

    @Test
    fun `malformed json returns null instead of crashing`() {
        assertNull(EspMinerParser.parseSystemInfo(fixture("synthetic_malformed.json")))
        assertNull(EspMinerParser.parseSystemInfo(""))
        assertNull(EspMinerParser.parseSystemInfo("[]"))
        assertNull(EspMinerParser.parseSystemInfo("{}"))
    }
}
