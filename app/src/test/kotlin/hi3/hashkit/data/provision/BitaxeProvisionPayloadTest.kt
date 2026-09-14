package hi3.hashkit.data.provision

import hi3.hashkit.adapters.espminer.EspMinerFlavor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitaxeProvisionPayloadTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String) =
        json.parseToJsonElement(
            checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/espminer/$name")).bufferedReader().readText(),
        ).jsonObject

    private val pool = BitaxeProvisioner.PoolSetup("public-pool.io", 21496, "bc1qexample.axe")

    @Test
    fun `flat-pool firmware gets wifi, hostname and flat pool keys`() {
        val body = BitaxeProvisioner.buildPayload(
            fixture("real_bm1370_v2.14.2.json"), EspMinerFlavor.OFFICIAL_V2_FLAT_POOLS,
            BitaxeProvisioner.WifiSetup("HomeWifi", "hunter2", " desk-axe "), pool,
        )!!
        assertEquals("HomeWifi", body["ssid"]!!.jsonPrimitive.content)
        assertEquals("hunter2", body["wifiPass"]!!.jsonPrimitive.content)
        assertEquals("desk-axe", body["hostname"]!!.jsonPrimitive.content)
        assertEquals("public-pool.io", body["stratumURL"]!!.jsonPrimitive.content)
        assertEquals("21496", body["stratumPort"]!!.jsonPrimitive.content)
        assertTrue("pools" !in body)
    }

    @Test
    fun `pools-array firmware edits the primary entry and echoes the password mask`() {
        val body = BitaxeProvisioner.buildPayload(
            fixture("real_bm1366_v2.15.1.json"), EspMinerFlavor.OFFICIAL_V2_POOLS_ARRAY,
            BitaxeProvisioner.WifiSetup("HomeWifi", ""), pool,
        )!!
        assertTrue("stratumURL" !in body)
        assertTrue("hostname" !in body)
        val pools = body["pools"]!!.jsonArray
        assertEquals(2, pools.size)
        val primary = pools[0].jsonObject
        assertEquals("public-pool.io", primary["stratumURL"]!!.jsonPrimitive.content)
        assertEquals("bc1qexample.axe", primary["stratumUser"]!!.jsonPrimitive.content)
        assertEquals("*****", primary["stratumPassword"]!!.jsonPrimitive.content)
        assertEquals("192.0.2.10", pools[1].jsonObject["stratumURL"]!!.jsonPrimitive.content)
    }

    @Test
    fun `wifi only when no pool is given, and forks are refused`() {
        val body = BitaxeProvisioner.buildPayload(
            fixture("real_bm1370_v2.14.2.json"), EspMinerFlavor.OFFICIAL_V2_FLAT_POOLS,
            BitaxeProvisioner.WifiSetup("HomeWifi", "pw"), null,
        )!!
        assertEquals(setOf("ssid", "wifiPass"), body.keys)
        assertNull(
            BitaxeProvisioner.buildPayload(
                fixture("real_bm1370_v1.1.0.json"), EspMinerFlavor.NERDQAXE, BitaxeProvisioner.WifiSetup("HomeWifi", "pw"), pool,
            ),
        )
    }
}
