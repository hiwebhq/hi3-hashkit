package hi3.hashkit.adapters.futurebit

import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.ValueSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Apollo OS GraphQL fixtures. `apollo_auth_status.json` and `apollo_needs_login.json` are
 * verbatim captures from an Apollo II (2026-09-18); the stats fixture follows the
 * introspected schema (see ApolloAdapter.STATS_QUERY).
 */
class ApolloParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/futurebit/$name"))
            .bufferedReader().readText()

    private fun data(name: String): JsonObject =
        json.parseToJsonElement(fixture(name)).jsonObject["data"]!!.jsonObject

    @Test
    fun `auth status fingerprints an Apollo without a token`() {
        val d = data("apollo_auth_status.json")
        assertFalse(ApolloGraphQlClient.hasAuthError(d))
        assertEquals("done", (d["Auth"] as JsonObject).let {
            ((it["status"] as JsonObject)["result"] as JsonObject)["status"].toString().trim('"')
        })
    }

    @Test
    fun `login-required responses are detected as authentication errors`() {
        val d = data("apollo_needs_login.json")
        assertTrue(ApolloGraphQlClient.hasAuthError(d))
        assertEquals("You have to login first", ApolloGraphQlClient.authErrorMessage(d))
        assertNull(ApolloParser.parse(d, "10.0.0.10"))
    }

    @Test
    fun `identity prefers the LAN interface MAC and reads hostname and versions`() {
        val parsed = ApolloParser.parse(data("apollo_stats_synthetic.json"), "10.0.0.10")!!
        val id = parsed.identity
        assertEquals("5c:8a:ae:12:34:56", id.macAddress)
        assertEquals("apollo-ii", id.hostname)
        assertEquals("apollo-unit-0001", id.serialNumber)
        assertEquals("FutureBit", id.manufacturer)
        assertEquals("Apollo OS", id.firmwareFamily)
        assertEquals("Apollo OS 2.0.5, miner apollo-miner 2.0.5", id.firmwareVersion)
        assertEquals("mac:5c:8a:ae:12:34:56", id.stableKey("10.0.0.10"))
    }

    @Test
    fun `telemetry uses the 5-minute window and aggregates board data`() {
        val now = Instant.parse("2026-09-18T21:40:00Z")
        val t = ApolloParser.parse(data("apollo_stats_synthetic.json"), "10.0.0.10", now)!!.telemetry
        assertEquals(MinerStatus.ONLINE, t.status)
        assertEquals(8005.2, t.hashrateGhs.value!!, 0.001)
        assertEquals(ValueSource.REPORTED, t.hashrateGhs.source)
        assertEquals(285.5, t.powerW.value!!, 0.001)
        assertEquals(ValueSource.MEASURED, t.powerW.source)
        // 285.5 W / 8.0052 TH/s
        assertEquals(35.66, t.efficiencyJTh.value!!, 0.01)
        assertEquals(ValueSource.CALCULATED, t.efficiencyJTh.source)
        assertEquals(54.0, t.chipTempC.value!!, 0.001)
        assertEquals(575.0, t.frequencyMhz.value!!, 0.001)
        assertEquals(44, t.asicCount)
        assertEquals(1497L, t.sharesAccepted)
        assertEquals(3L, t.sharesRejected)
        assertEquals(86400L, t.uptimeSeconds)
        assertEquals(listOf(3200, 3180), t.fans.map { it.rpm })
        assertEquals(listOf(60, 60), t.fans.map { it.percent })
        assertEquals(1, t.perChain.size)
        assertEquals(8005.2, t.perChain[0].hashrateGhs!!, 0.001)
        assertEquals(44, t.perChain[0].chipsActive)
        assertEquals("balanced", t.unrecognizedFields["minerMode"])
    }

    @Test
    fun `pool comes from the miner record unless it is a local proxy`() {
        val d = data("apollo_stats_synthetic.json")
        val direct = ApolloParser.parse(d, "10.0.0.10")!!.telemetry
        assertEquals("10.0.0.42", direct.poolUrl)
        assertEquals(3333, direct.poolPort)
        assertEquals("bc1qexampleworker.apollo", direct.workerName)

        val viaProxy = fixture("apollo_stats_synthetic.json")
            .replace("\"host\":\"10.0.0.42\"", "\"host\":\"127.0.0.1\"")
        val t = ApolloParser.parse(json.parseToJsonElement(viaProxy).jsonObject["data"]!!.jsonObject, "10.0.0.10")!!.telemetry
        assertEquals("pool.example.invalid", t.poolUrl)
        assertEquals(3333, t.poolPort)
    }

    @Test
    fun `stopped miner reports OFFLINE`() {
        val stopped = fixture("apollo_stats_synthetic.json").replace("\"status\":true", "\"status\":false")
        val t = ApolloParser.parse(json.parseToJsonElement(stopped).jsonObject["data"]!!.jsonObject, "10.0.0.10")!!.telemetry
        assertEquals(MinerStatus.OFFLINE, t.status)
    }

    @Test
    fun `probe identity carries only the family`() {
        val id = ApolloParser.probeIdentity()
        assertEquals("FutureBit", id.manufacturer)
        assertNotNull(id.model)
        assertNull(id.macAddress)
        assertEquals("ip:10.0.0.10", id.stableKey("10.0.0.10"))
    }

    @Test
    fun `splitPool handles scheme and bare host`() {
        assertEquals("pool.example.invalid" to 3333, ApolloParser.splitPool("stratum+tcp://pool.example.invalid:3333"))
        assertEquals("10.0.0.42" to null, ApolloParser.splitPool("10.0.0.42"))
        assertEquals("10.0.0.42" to 3333, ApolloParser.splitPool("10.0.0.42:3333/extra"))
    }
}
