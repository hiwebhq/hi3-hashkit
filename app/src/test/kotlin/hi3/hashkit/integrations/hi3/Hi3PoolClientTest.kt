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

/**
 * Response shapes verified live against pool.hi3.cc (public-pool fork, v0.23.x) and
 * its compiled backend controller on 2026-09-06.
 */
class Hi3PoolClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: Hi3PoolClient

    // Shape from GET /api/client/{address} (worker fields confirmed in backend dist).
    private val accountBody = """
        {"workersCount":2,"workers":[
          {"sessionId":"abc123","name":"0x203","bestDifficulty":39168237623.65,
           "hashRate":521794677700.0,"startTime":"2026-09-01T00:00:00.000Z","lastSeen":"2026-09-06T21:00:00.000Z"},
          {"sessionId":"def456","name":"GammaHex1","bestDifficulty":2861213607.0,
           "hashRate":8640476562500.0,"startTime":"2026-09-01T00:00:00.000Z","lastSeen":"2026-09-06T21:00:00.000Z"}
        ]}
    """.trimIndent()

    // Shape from live GET /api/network (bitcoind-style).
    private val networkBody = """
        {"blocks":965883,"difficulty":127450789715843.1,"networkhashps":1.083e21,"chain":"main"}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = Hi3PoolClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun base() = "http://127.0.0.1:${server.port}"

    @Test
    fun `parses account and converts hashrate to GHs`() = runTest {
        server.enqueue(MockResponse().setBody(accountBody))
        val result = client.fetchAccount(base(), "bc1qexampleaddressabcdefghij")
        val account = (result as Hi3PoolClient.PoolResult.Ok).value
        assertEquals(2, account.workersCount)
        assertEquals("0x203", account.workers[0].name)
        // 521794677700 H/s -> 521.79 GH/s
        assertEquals(521.79, account.workers[0].hashRateGhs!!, 0.01)
        assertEquals(8640.47, account.workers[1].hashRateGhs!!, 0.01)
        assertEquals(9162.27, account.totalHashRateGhs, 0.01)
        assertEquals(39168237623.65, account.workers[0].bestDifficulty!!, 1.0)
        // Request path carries only the address.
        assertEquals("/api/client/bc1qexampleaddressabcdefghij", server.takeRequest().path)
    }

    @Test
    fun `empty account parses to zero workers`() = runTest {
        server.enqueue(MockResponse().setBody("""{"workersCount":0,"workers":[]}"""))
        val account = (client.fetchAccount(base(), "bc1qexampleaddressabcdefghij")
            as Hi3PoolClient.PoolResult.Ok).value
        assertEquals(0, account.workersCount)
        assertTrue(account.workers.isEmpty())
    }

    @Test
    fun `parses network difficulty`() = runTest {
        server.enqueue(MockResponse().setBody(networkBody))
        val net = (client.fetchNetwork(base()) as Hi3PoolClient.PoolResult.Ok).value
        assertEquals(127450789715843.1, net.difficulty!!, 1.0)
        assertEquals(965883L, net.blockHeight)
    }

    @Test
    fun `http is refused for public hosts and allowed for private ones`() {
        assertNotNull(client.validateBaseUrl("http://pool.hi3.cc"))
        assertNull(client.validateBaseUrl("https://pool.hi3.cc"))
        assertNull(client.validateBaseUrl("http://10.0.0.42:8080"))     // private stage
        assertNull(client.validateBaseUrl("http://100.91.15.48:8080")) // tailnet
        assertNotNull(client.validateBaseUrl("not a url"))
    }

    @Test
    fun `invalid addresses are rejected before any request`() = runTest {
        val bad = client.fetchAccount(base(), "has spaces / weird")
        assertTrue(bad is Hi3PoolClient.PoolResult.Error)
        val empty = client.fetchAccount(base(), "  ")
        assertTrue(empty is Hi3PoolClient.PoolResult.Error)
        assertEquals(0, server.requestCount)
    }

    // Shape verified live against the Hi3 stratum proxy /sproxy-api/api/v1/sessions.
    private val sessionsBody = """
        {"count":2,"sessions":[
          {"authorized":true,"hashrate":7122935628362.281,"peer":"10.0.0.48:60870",
           "shares_accepted":4310,"shares_rejected":1,"shares_stale":0,
           "worker":"34T82addr.0x48"},
          {"authorized":true,"hashrate":376829973418.79,"peer":"10.0.0.203:58581",
           "shares_accepted":4242,"shares_rejected":11,"shares_stale":1,
           "worker":"34T82addr.0x203"},
          {"hashrate":999.0,"peer":"10.0.0.9:1","worker":"OTHERaddr.0x9"}
        ]}
    """.trimIndent()

    @Test
    fun `sessions parse peer host and hashrate and filter by address`() = runTest {
        server.enqueue(MockResponse().setBody(sessionsBody))
        val res = client.fetchSessions(base(), "34T82addr")
        val list = (res as Hi3PoolClient.PoolResult.Ok).value
        assertEquals(2, list.size) // OTHERaddr session filtered out
        assertEquals("10.0.0.48", list[0].peerHost)
        assertEquals(7122.94, list[0].hashRateGhs!!, 0.01)
        assertEquals(4310L, list[0].sharesAccepted)
        assertEquals("/sproxy-api/api/v1/sessions", server.takeRequest().path)
    }

    @Test
    fun `http errors and malformed bodies degrade to errors not crashes`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(client.fetchAccount(base(), "bc1qexampleaddressabcdefghij") is Hi3PoolClient.PoolResult.Error)
        server.enqueue(MockResponse().setBody("not json at all"))
        assertTrue(client.fetchAccount(base(), "bc1qexampleaddressabcdefghij") is Hi3PoolClient.PoolResult.Error)
    }
}
