package hi3.hashkit.integrations.metrics

import hi3.hashkit.data.repo.MinerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.net.Socket
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * A tiny embedded HTTP server exposing GET /metrics in Prometheus format, so a local
 * Prometheus/Grafana can scrape the fleet. Opt-in, off by default, and an Advanced feature.
 *
 * Deliberately minimal and read-only: it answers only GET /metrics (everything else 404),
 * serves no files, accepts no input beyond the request line, and returns only the same
 * aggregate/per-miner gauges the app already shows. There is no auth (Prometheus scrapes
 * are unauthenticated) — intended for a private LAN/tailnet, which the docs make clear.
 */
@Singleton
class PrometheusServer @Inject constructor(
    private val repository: MinerRepository,
) {
    @Volatile private var server: ServerSocket? = null
    @Volatile private var accept: Thread? = null
    @Volatile var runningPort: Int = -1
        private set

    @Synchronized
    fun apply(enabled: Boolean, port: Int) {
        if (enabled && runningPort == port && server != null) return
        stop()
        if (enabled) start(port)
    }

    @Synchronized
    private fun start(port: Int) {
        runCatching {
            val s = ServerSocket(port)
            server = s
            runningPort = port
            accept = thread(name = "prometheus-metrics", isDaemon = true) {
                while (!s.isClosed) {
                    val socket = runCatching { s.accept() }.getOrNull() ?: break
                    runCatching { handle(socket) }
                    runCatching { socket.close() }
                }
            }
        }.onFailure { runningPort = -1 }
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        accept = null
        runningPort = -1
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 3000
        val reader = socket.getInputStream().bufferedReader()
        val requestLine = reader.readLine() ?: return
        // Drain the rest of the request headers (we don't use them).
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }
        val out = socket.getOutputStream()
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0)
        val path = parts.getOrNull(1)?.substringBefore('?')
        if (method != "GET") {
            respond(out, "404 Not Found", "text/plain; charset=utf-8", "Not found.\n")
            out.flush()
            return
        }
        when (path) {
            "/metrics" -> {
                val body = runCatching {
                    runBlocking { MetricsFormatter.render(currentMiners()) }
                }.getOrDefault("# metrics unavailable\n")
                respond(out, "200 OK", "text/plain; version=0.0.4; charset=utf-8", body)
            }
            "/", "/dashboard", "/index.html" -> {
                val body = runCatching {
                    runBlocking { HtmlDashboard.render(currentMiners()) }
                }.getOrDefault("<html><body>dashboard unavailable</body></html>")
                respond(out, "200 OK", "text/html; charset=utf-8", body)
            }
            else -> respond(
                out, "404 Not Found", "text/plain; charset=utf-8",
                "Not found. Try GET / (dashboard) or /metrics\n",
            )
        }
        out.flush()
    }

    private suspend fun currentMiners() =
        repository.observeMinerEntities().first().map { repository.toDomain(it, Instant.now()) }

    private fun respond(out: java.io.OutputStream, status: String, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
    }
}
