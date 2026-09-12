package hi3.hashkit.integrations.hashpower

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BraiinsHashpowerClientTest {

    private val server = MockWebServer()
    private lateinit var client: BraiinsHashpowerClient

    @Before
    fun setUp() {
        server.start()
        client = BraiinsHashpowerClient(OkHttpClient()).apply {
            baseUrl = server.url("/v1").toString().trimEnd('/')
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** The live API returns sat prices as integers or decimals (last_avg_price_sat went
     *  fractional 2026-09); both must parse, decimals rounding to whole sats. */
    @Test
    fun `fetch parses decimal sat prices`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"volume_24h_m":5259996.402522,"best_ask_sat":49055000,
                   "last_avg_price_sat":53259756.76381,"hash_rate_available_10m_ph":5455.43,
                   "hash_rate_matched_10m_ph":299.03,"best_bid_sat":65807000,
                   "status":"SPOT_INSTRUMENT_STATUS_ACTIVE"}""".trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """{"asks":[{"hr_matched_ph":53.73,"price_sat":49055000.5,"hr_available_ph":53.73}]}"""
            )
        )
        val result = client.fetch()
        assertTrue("expected Ok, got $result", result is BraiinsHashpowerClient.Result.Ok)
        val ok = result as BraiinsHashpowerClient.Result.Ok
        assertEquals(53259757L, ok.market.lastAvgPriceSat)
        assertEquals(49055000L, ok.market.bestAskSat)
        assertEquals(65807000L, ok.market.bestBidSat)
        assertEquals(49055001L, ok.asks.single().priceSat)
    }
}
