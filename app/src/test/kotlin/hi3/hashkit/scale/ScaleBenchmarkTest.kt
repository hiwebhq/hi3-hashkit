package hi3.hashkit.scale

import hi3.hashkit.adapters.espminer.EspMinerAdapter
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.domain.adapter.TelemetryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fleet-scale simulation on loopback: N lightweight HTTP servers each impersonating an
 * ESP-Miner `/api/system/info`, polled through the real adapter + parser with the same
 * concurrency the in-app poller uses. Produces the timing numbers documented in
 * docs/LIMITS.md. This is a JVM simulation — it measures the app's own pipeline
 * (connections, parsing, concurrency), not Wi-Fi latency or Android scheduling.
 */
class ScaleBenchmarkTest {

    companion object {
        private lateinit var servers: List<ServerSocket>
        private lateinit var fixture: String
        private const val MAX_MINERS = 250

        @JvmStatic
        @BeforeClass
        fun startServers() {
            fixture = checkNotNull(
                ScaleBenchmarkTest::class.java.classLoader
                    ?.getResourceAsStream("fixtures/espminer/real_bm1370_v2.14.2.json")
            ).bufferedReader().readText()
            val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                "Content-Length: ${fixture.toByteArray().size}\r\nConnection: close\r\n\r\n" + fixture
            servers = (0 until MAX_MINERS).map {
                val server = ServerSocket(0, 64)
                Thread {
                    while (!server.isClosed) {
                        runCatching {
                            val socket = server.accept()
                            socket.use { s ->
                                // Drain the request line + headers, then answer.
                                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isEmpty()) break
                                }
                                s.getOutputStream().write(response.toByteArray())
                                s.getOutputStream().flush()
                            }
                        }
                    }
                }.apply { isDaemon = true; start() }
                server
            }
        }

        @JvmStatic
        @AfterClass
        fun stopServers() {
            servers.forEach { runCatching { it.close() } }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val adapter = EspMinerAdapter(client)

    /** Poll [n] simulated miners with the poller's concurrency; return elapsed ms. */
    private fun pollAll(n: Int, concurrency: Int = 32): Long = runBlocking {
        val ok = AtomicInteger(0)
        val semaphore = Semaphore(concurrency)
        val start = System.nanoTime()
        (0 until n).map { i ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val result = adapter.getTelemetry(MinerHost("127.0.0.1", servers[i].localPort))
                    if (result is TelemetryResult.Success) ok.incrementAndGet()
                }
            }
        }.awaitAll()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        // The single-threaded stub servers occasionally drop one concurrent connect;
        // this is a benchmark, so >=99% success is the bar (the app itself treats a
        // dropped poll as one offline sample and retries next cycle).
        assert(ok.get() >= n * 99 / 100) { "only ${ok.get()}/$n polls succeeded" }
        elapsedMs
    }

    @Test
    fun `fleet poll scales - prints measurements for LIMITS doc`() {
        // Warm-up (JIT, connection pool).
        pollAll(10)
        val results = listOf(1, 10, 50, 100, 250).map { n ->
            val ms = pollAll(n)
            n to ms
        }
        println("=== Fleet poll benchmark (loopback simulation, concurrency 32) ===")
        results.forEach { (n, ms) -> println("miners=$n pollCycleMs=$ms") }
        // Regression guard, deliberately generous: a 250-miner cycle through the real
        // adapter+parser must finish well inside one 15 s poll interval.
        val ms250 = results.last().second
        assert(ms250 < 10_000) { "250-miner poll took ${ms250}ms" }
    }

    @Test
    fun `parser throughput is not the bottleneck`() {
        val warm = (0 until 500).sumOf {
            hi3.hashkit.adapters.espminer.EspMinerParser.parseSystemInfo(fixture)!!
                .telemetry.hashrateGhs.value ?: 0.0
        }
        val start = System.nanoTime()
        val iterations = 2_000
        repeat(iterations) {
            hi3.hashkit.adapters.espminer.EspMinerParser.parseSystemInfo(fixture)
        }
        val perParseUs = (System.nanoTime() - start) / 1_000.0 / iterations
        println("=== Parser benchmark: %.1f µs/response (99-key real fixture; warm=$warm) ===".format(perParseUs))
        assert(perParseUs < 5_000) { "Parsing took ${perParseUs}µs per response" }
    }
}
