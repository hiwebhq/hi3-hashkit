package hi3.hashkit.integrations.hi3

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Shapes from the deployed MMP OpenAPI contract (Hi3 MMP 0.16.x), 2026-09-06. */
class MmpClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: MmpClient

    private val summaryBody = """
        {"generated_at":"2026-09-06T22:00:00Z","stale_after_seconds":120,
         "by_state":{"mining":42,"offline":3},
         "installed":45,"online":42,"healthy":40,"needs_attention":2,"zero_hash":1,
         "hashrate_ths":4123.5,"hashrate_nominal_ths":4400.0,
         "hashrate_realization_pct":93.7,"power_kw":128.4,
         "efficiency_jth":31.1,"availability_pct":93.3,"data_quality":"ok"}
    """.trimIndent()

    private val bySiteBody = """
        [{"site_id":"s1","site_name":"Home Lab","installed":8,"online":8,"hashrate_ths":38.9,"power_kw":0.66},
         {"site_id":"s2","site_name":"Remote Site","installed":37,"online":34,"hashrate_ths":4084.6,"power_kw":127.8}]
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = MmpClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun base() = "http://127.0.0.1:${server.port}"

    @Test
    fun `parses fleet summary and sends bearer auth`() = runTest {
        server.enqueue(MockResponse().setBody(summaryBody))
        val result = client.fetchFleetSummary(base(), "mmp_testkey_abc")
        val s = (result as MmpClient.MmpResult.Ok).value
        assertEquals(45, s.installed)
        assertEquals(42, s.online)
        assertEquals(2, s.needsAttention)
        assertEquals(4123.5, s.hashrateThs!!, 0.001)
        assertEquals(93.7, s.realizationPct!!, 0.001)
        assertEquals(31.1, s.efficiencyJth!!, 0.001)
        val request = server.takeRequest()
        assertEquals("/api/v1/fleet/summary", request.path)
        assertEquals("Bearer mmp_testkey_abc", request.getHeader("Authorization"))
    }

    @Test
    fun `parses site rollups`() = runTest {
        server.enqueue(MockResponse().setBody(bySiteBody))
        val sites = (client.fetchBySite(base(), "mmp_k") as MmpClient.MmpResult.Ok).value
        assertEquals(2, sites.size)
        assertEquals("Home Lab", sites[0].siteName)
        assertEquals(38.9, sites[0].hashrateThs!!, 0.001)
        assertEquals(34, sites[1].online)
    }

    @Test
    fun `401 and 403 map to distinct actionable messages`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Authentication required."}"""))
        val unknown = client.fetchFleetSummary(base(), "mmp_bad") as MmpClient.MmpResult.Error
        assertTrue(unknown.message.contains("does not recognize"))

        server.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
        val forbidden = client.fetchFleetSummary(base(), "mmp_scoped") as MmpClient.MmpResult.Error
        assertTrue(forbidden.message.contains("lacks"))
    }

    @Test
    fun `blank key never sends a request`() = runTest {
        val result = client.fetchFleetSummary(base(), "")
        assertTrue(result is MmpClient.MmpResult.Error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `https required for public hosts`() {
        assertNotNull(client.validateBaseUrl("http://mmp.hi3.cc"))
        assertNull(client.validateBaseUrl("https://mmp.hi3.cc"))
        assertNull(client.validateBaseUrl("http://10.0.0.42:8082")) // stage
    }

    @Test
    fun `malformed bodies degrade to errors`() = runTest {
        server.enqueue(MockResponse().setBody("<html>oops</html>"))
        assertTrue(client.fetchFleetSummary(base(), "mmp_k") is MmpClient.MmpResult.Error)
    }
}
