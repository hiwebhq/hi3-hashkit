package hi3.hashkit.adapters.espminer

import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.domain.adapter.FanControl
import hi3.hashkit.domain.adapter.MinerHost
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Control-path tests against recorded firmware fixtures served by MockWebServer on
 * loopback (an allowed address). These lock in the safety-critical behaviors verified
 * from the ESP-Miner source: password-mask echoing, firmware gating, and
 * option-list-only tuning.
 */
class EspMinerControlTest {

    private lateinit var server: MockWebServer
    private lateinit var adapter: EspMinerAdapter
    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/espminer/$name"))
            .bufferedReader().readText()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = EspMinerAdapter(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun host(credential: String? = null) = MinerHost("127.0.0.1", server.port, credential)

    @Test
    fun `v2_15 pool edit echoes masked passwords and edits only the primary pool`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("real_bm1366_v2.15.1.json"))) // gate + info
        server.enqueue(MockResponse().setBody("{}")) // PATCH response

        val result = adapter.setPrimaryPool(host(), "new.pool.example", 4444, "bc1qnewworker.axe")
        assertTrue(result is ActionResult.Success)

        server.takeRequest() // GET /api/system/info
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        assertEquals("/api/system", patch.path)

        val body = json.parseToJsonElement(patch.body.readUtf8()).jsonObject
        val pools = body["pools"]!!.jsonArray
        assertEquals(2, pools.size)

        val primary = pools[0].jsonObject
        assertEquals("new.pool.example", primary["stratumURL"]!!.jsonPrimitive.content)
        assertEquals("4444", primary["stratumPort"]!!.jsonPrimitive.content)
        assertEquals("bc1qnewworker.axe", primary["stratumUser"]!!.jsonPrimitive.content)
        // The mask MUST be echoed — omitting it would wipe the stored password.
        assertEquals("*****", primary["stratumPassword"]!!.jsonPrimitive.content)

        // The secondary pool is untouched, mask included.
        val secondary = pools[1].jsonObject
        assertEquals("*****", secondary["stratumPassword"]!!.jsonPrimitive.content)
        assertEquals("192.0.2.10", secondary["stratumURL"]!!.jsonPrimitive.content)
    }

    @Test
    fun `v2_14 pool edit uses flat fields`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("real_bm1370_v2.14.2.json")))
        server.enqueue(MockResponse().setBody("{}"))

        val result = adapter.setPrimaryPool(host(), "new.pool.example", 4444, "worker1")
        assertTrue(result is ActionResult.Success)

        server.takeRequest()
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("new.pool.example", body["stratumURL"]!!.jsonPrimitive.content)
        assertEquals("4444", body["stratumPort"]!!.jsonPrimitive.content)
        assertEquals("worker1", body["stratumUser"]!!.jsonPrimitive.content)
        assertTrue("pools" !in body)
    }

    @Test
    fun `bc01 fork refuses unverified controls but reboot goes through`() = runTest {
        // Pool/fan semantics are unverified on this fork — refused after only the gating GET.
        server.enqueue(MockResponse().setBody(fixture("real_variant_2.0.0_string_bestdiff.json")))
        val refused = adapter.setFan(host(), FanControl.Manual(80))
        assertTrue(refused is ActionResult.Unsupported)
        assertEquals(1, server.requestCount)

        // Reboot is live-verified on BC01 (2026-09-13) — the restart POST reaches the miner.
        server.enqueue(MockResponse().setBody(fixture("real_variant_2.0.0_string_bestdiff.json")))
        server.enqueue(MockResponse().setBody("{}"))
        val result = adapter.reboot(host())
        assertTrue(result is ActionResult.Success)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `pool and fan controls are refused on nerdqaxe firmware`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("real_bm1370_v1.1.0.json")))
        val result = adapter.setFan(host(), FanControl.Manual(80))
        assertTrue(result is ActionResult.Unsupported)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `tune applies approved values on nerdqaxe firmware`() = runTest {
        // NerdQAxe v1.1.0: PATCH frequency/coreVoltage is verified (applied live by the
        // power-management task), and /api/system/asic publishes the approved options.
        server.enqueue(MockResponse().setBody(fixture("real_bm1370_v1.1.0.json"))) // gate
        server.enqueue(
            MockResponse().setBody(
                """{"frequencyOptions":[500,600,750],"voltageOptions":[1200,1260]}"""
            )
        ) // asic options
        server.enqueue(MockResponse().setBody("{}")) // PATCH response

        val result = adapter.applyTune(host(), 600, 1200)
        assertTrue(result is ActionResult.Success)
        server.takeRequest(); server.takeRequest()
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        assertEquals("/api/system", patch.path)
        val body = json.parseToJsonElement(patch.body.readUtf8()).jsonObject
        assertEquals("600", body["frequency"]!!.jsonPrimitive.content)
        assertEquals("1200", body["coreVoltage"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tune rejects values outside the firmware-approved lists`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("real_bm1370_v2.14.2.json"))) // gate
        server.enqueue(
            MockResponse().setBody(
                """{"frequencyOptions":[400,490,525],"voltageOptions":[1000,1150],
                   "defaultFrequency":525,"defaultVoltage":1150}"""
            )
        ) // asic options

        val result = adapter.applyTune(host(), 999, 1150)
        assertTrue(result is ActionResult.Failure)
        assertTrue((result as ActionResult.Failure).message.contains("not in the firmware-approved list"))
        assertEquals(2, server.requestCount) // no PATCH was sent
    }

    @Test
    fun `tune applies approved values`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("real_bm1370_v2.14.2.json")))
        server.enqueue(
            MockResponse().setBody(
                """{"frequencyOptions":[400,490,525],"voltageOptions":[1000,1150]}"""
            )
        )
        server.enqueue(MockResponse().setBody("{}"))

        val result = adapter.applyTune(host(), 490, 1150)
        assertTrue(result is ActionResult.Success)
        server.takeRequest(); server.takeRequest()
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("490", body["frequency"]!!.jsonPrimitive.content)
        assertEquals("1150", body["coreVoltage"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tune on otp-protected nerdqaxe mints a session token and retries, then reuses it`() = runTest {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val asicOptions = """{"frequencyOptions":[500,600,750],"voltageOptions":[1200,1260]}"""

        // First tune: PATCH is refused with 401, a session is minted, PATCH retried.
        server.enqueue(MockResponse().setBody(fixture("real_bm1370_v1.1.0.json"))) // gate
        server.enqueue(MockResponse().setBody(asicOptions)) // asic options
        server.enqueue(MockResponse().setResponseCode(401)) // PATCH → OTP required
        server.enqueue(MockResponse().setBody("""{"token":"SESSTOKEN.ABC","ttlMs":86400000}"""))
        server.enqueue(MockResponse().setBody("{}")) // retried PATCH

        val result = adapter.applyTune(host(credential = secret), 600, 1200)
        assertTrue(result is ActionResult.Success)

        server.takeRequest(); server.takeRequest() // gate GET, asic GET
        val refused = server.takeRequest()
        assertEquals("PATCH", refused.method)
        assertEquals(null, refused.getHeader("X-OTP-Session"))
        val mint = server.takeRequest()
        assertEquals("POST", mint.method)
        assertEquals("/api/otp/session", mint.path)
        // A 6-digit TOTP code computed from the stored secret.
        assertTrue(mint.getHeader("X-TOTP")!!.matches(Regex("\\d{6}")))
        val retried = server.takeRequest()
        assertEquals("PATCH", retried.method)
        assertEquals("SESSTOKEN.ABC", retried.getHeader("X-OTP-Session"))

        // Second tune: the cached session token is attached up front — no 401 round trip.
        server.enqueue(MockResponse().setBody(fixture("real_bm1370_v1.1.0.json")))
        server.enqueue(MockResponse().setBody(asicOptions))
        server.enqueue(MockResponse().setBody("{}"))
        assertTrue(adapter.applyTune(host(credential = secret), 750, 1260) is ActionResult.Success)
        server.takeRequest(); server.takeRequest()
        assertEquals("SESSTOKEN.ABC", server.takeRequest().getHeader("X-OTP-Session"))
    }

    @Test
    fun `tune on otp-protected nerdqaxe without a stored secret fails with guidance`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("real_bm1370_v1.1.0.json")))
        server.enqueue(
            MockResponse().setBody(
                """{"frequencyOptions":[500,600,750],"voltageOptions":[1200,1260]}"""
            )
        )
        server.enqueue(MockResponse().setResponseCode(401))

        val result = adapter.applyTune(host(), 600, 1200)
        assertTrue(result is ActionResult.Failure)
        assertTrue((result as ActionResult.Failure).message.contains("TOTP secret"))
        assertEquals(3, server.requestCount) // no session mint was attempted
    }

    @Test
    fun `reboot posts to the restart endpoint after gating`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("real_bm1366_v2.15.1.json")))
        server.enqueue(MockResponse().setBody("{}"))

        val result = adapter.reboot(host())
        assertTrue(result is ActionResult.Success)
        server.takeRequest()
        val restart = server.takeRequest()
        assertEquals("POST", restart.method)
        assertEquals("/api/system/restart", restart.path)
    }
}
