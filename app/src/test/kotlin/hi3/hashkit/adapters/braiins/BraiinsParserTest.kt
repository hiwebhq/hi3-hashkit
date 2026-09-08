package hi3.hashkit.adapters.braiins

import hi3.hashkit.domain.model.ValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures captured from a real Braiins Mini Miner BMM 100 (boser-openwrt 0.1.0). */
class BraiinsParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/braiins/$name"))
            .bufferedReader().readText()

    @Test
    fun `version identifies boser and rejects others`() {
        assertTrue(BraiinsParser.isBoser(fixture("bmm100_version.json")))
        assertFalse(BraiinsParser.isBoser("""{"VERSION":[{"CGMiner":"4.11.1","PROD":"Avalonnano"}]}"""))
        assertFalse(BraiinsParser.isBoser("not json"))
        assertFalse(BraiinsParser.isBoser("{}"))
    }

    @Test
    fun `identity uses devdetails model`() {
        val id = BraiinsParser.identityOf(fixture("bmm100_version.json"), fixture("bmm100_devdetails.json"))
        assertEquals("Braiins", id.manufacturer)
        assertEquals("Braiins Mini Miner BMM 100", id.model)
        assertEquals("Braiins OS (BOSer)", id.firmwareFamily)
        assertEquals("boser-openwrt 0.1.0-fc9fe388", id.firmwareVersion)
    }

    @Test
    fun `telemetry maps MHS to GHs and reads temps fans details`() {
        val t = BraiinsParser.parseTelemetry(
            fixture("bmm100_summary.json"),
            fixture("bmm100_devs.json"),
            fixture("bmm100_temps.json"),
            fixture("bmm100_fans.json"),
            fixture("bmm100_devdetails.json"),
            fixture("bmm100_pools.json"),
        )
        // MHS 5m = 1473003.67 MH/s -> 1473.0 GH/s
        assertEquals(1473.0, t.hashrateGhs.value!!, 0.01)
        // Nominal MHS = 1521123.65 -> 1521.12 GH/s (firmware-declared expected)
        assertEquals(1521.12, t.expectedHashrateGhs.value!!, 0.01)
        assertTrue(t.attainmentPercent!! in 95.0..99.0)
        assertEquals(68.0, t.chipTempC.value!!, 0.001)
        // No power sensor: UNAVAILABLE, never estimated silently.
        assertEquals(ValueSource.UNAVAILABLE, t.powerW.source)
        assertNull(t.powerW.value)
        assertEquals(1973, t.fans.single().rpm)
        assertEquals(88, t.fans.single().percent)
        assertEquals(739.0, t.frequencyMhz.value!!, 0.001)
        assertEquals(1420.0, t.coreVoltageMv.value!!, 0.001)
        assertEquals(4, t.asicCount)
        assertEquals(323094L, t.sharesAccepted)
        assertEquals(12856L, t.sharesRejected)
        assertEquals(100000.0, t.bestDifficulty!!, 1.0)
        assertEquals(2527509L, t.uptimeSeconds)
        assertEquals("192.0.2.10", t.poolUrl)
        assertEquals("boardTempC", t.unrecognizedFields.keys.first { it == "boardTempC" })
    }

    @Test
    fun `partial responses degrade without inventing values`() {
        val summaryOnly = BraiinsParser.parseTelemetry(
            fixture("bmm100_summary.json"), null, null, null, null, null,
        )
        assertEquals(1473.0, summaryOnly.hashrateGhs.value!!, 0.01)
        assertNull(summaryOnly.chipTempC.value)
        assertTrue(summaryOnly.fans.isEmpty())
        assertNull(summaryOnly.expectedHashrateGhs.value)

        val nothing = BraiinsParser.parseTelemetry("garbage", "{]", null, null, null, null)
        assertNull(nothing.hashrateGhs.value)
    }
}

/** Braiins now exposes verified Pause/Resume (BOSminer pause/resume); no other controls. */
class BraiinsControlTest {
    private val adapter = BraiinsAdapter(hi3.hashkit.adapters.cgminer.CgMinerApi())

    @org.junit.Test
    fun `capabilities advertise power control only`() {
        val caps = adapter.getCapabilities(null)
        org.junit.Assert.assertTrue(caps.supported.contains(hi3.hashkit.domain.model.Capability.POWER_CONTROL))
        org.junit.Assert.assertTrue(caps.supported.contains(hi3.hashkit.domain.model.Capability.TELEMETRY))
        // Pool/reboot/fan/tune stay unsupported until the gRPC/web API is verified.
        org.junit.Assert.assertFalse(caps.supported.contains(hi3.hashkit.domain.model.Capability.SET_POOLS))
        org.junit.Assert.assertFalse(caps.supported.contains(hi3.hashkit.domain.model.Capability.REBOOT))
    }
}
