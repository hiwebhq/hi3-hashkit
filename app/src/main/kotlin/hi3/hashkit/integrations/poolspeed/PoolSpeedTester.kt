package hi3.hashkit.integrations.poolspeed

import hi3.hashkit.discovery.ConnectivityProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/** One pool's measured stratum speed. */
data class PoolSpeedResult(
    val label: String,
    val host: String,
    val port: Int,
    /** TCP handshake latency (min/avg over samples) and jitter (max−min), ms. */
    val connectMinMs: Long?,
    val connectAvgMs: Long?,
    val jitterMs: Long?,
    /** Application round-trip: time from connected to the pool's first stratum response, ms. */
    val subscribeMs: Long?,
    /** True when the endpoint answered `mining.subscribe` — i.e. it really is a stratum pool. */
    val speaksStratum: Boolean,
    val reachable: Boolean,
)

/**
 * On-demand pool stratum speed test. No packet sniffing / root: it measures the TCP
 * handshake RTT and, by acting as a brief stratum client, the application round-trip to the
 * pool's first response. Opt-in outbound to the public pool host the user already mines to
 * (a TCP connect + one `mining.subscribe` line — nothing else is sent).
 */
@Singleton
class PoolSpeedTester @Inject constructor(
    private val connectivityProbe: ConnectivityProbe,
) {
    suspend fun test(label: String, host: String, port: Int, samples: Int = 5): PoolSpeedResult {
        val connects = (1..samples).mapNotNull { connectivityProbe.tcpLatencyMs(host, port) }
        val subscribe = stratumSubscribeMs(host, port)
        val min = connects.minOrNull()
        val avg = if (connects.isEmpty()) null else connects.average().toLong()
        val jitter = if (connects.size < 2) null else (connects.max() - connects.min())
        return PoolSpeedResult(
            label = label,
            host = host,
            port = port,
            connectMinMs = min,
            connectAvgMs = avg,
            jitterMs = jitter,
            subscribeMs = subscribe,
            speaksStratum = subscribe != null,
            reachable = connects.isNotEmpty() || subscribe != null,
        )
    }

    /**
     * Connect, send a real `mining.subscribe`, and time the pool's first response line. Times
     * from *after* the TCP connect, so it's the pure application request/response RTT.
     */
    private suspend fun stratumSubscribeMs(host: String, port: Int, timeoutMs: Int = 4000): Long? =
        withContext(Dispatchers.IO) {
            if (host.isBlank() || port !in 1..65535) return@withContext null
            runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), timeoutMs)
                    s.soTimeout = timeoutMs
                    val start = System.nanoTime()
                    s.getOutputStream().apply {
                        write("""{"id":1,"method":"mining.subscribe","params":[]}""".toByteArray(Charsets.UTF_8))
                        write('\n'.code)
                        flush()
                    }
                    val line = s.getInputStream().bufferedReader().readLine()
                    if (line.isNullOrBlank() || "\"id\"" !in line && "\"method\"" !in line && "\"result\"" !in line && "\"error\"" !in line) {
                        null
                    } else {
                        (System.nanoTime() - start) / 1_000_000
                    }
                }
            }.getOrNull()
        }

    companion object {
        /**
         * Parse a stratum pool address into host + port. Accepts `stratum+tcp://host:port`,
         * `stratum+ssl://host:port`, `//host:port`, and bare `host:port` or `host`.
         */
        fun parseStratum(url: String, defaultPort: Int = 3333): Pair<String, Int>? {
            var s = url.trim()
            if (s.isEmpty()) return null
            // Strip any scheme (stratum+tcp://, stratum+ssl://, tcp://, //, …).
            s = s.substringAfter("://", s)
            s = s.removePrefix("//")
            s = s.substringBefore('/') // drop any path
            val host = s.substringBeforeLast(':', s).trim()
            val port = if (':' in s) s.substringAfterLast(':').trim().toIntOrNull() ?: defaultPort else defaultPort
            if (host.isEmpty() || port !in 1..65535) return null
            return host to port
        }
    }
}
