package hi3.hashkit.integrations.metrics

import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.discovery.MinerHostValidator
import hi3.hashkit.discovery.NetworkInspector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * A tiny embedded HTTP server exposing GET / (HTML dashboard) and GET /metrics (Prometheus
 * format) so a local Prometheus/Grafana or a TV browser can read the fleet. Opt-in, off by
 * default, an Advanced feature.
 *
 * Exposure is deliberately narrow:
 *  - It binds **only to the device's private LAN/Tailscale addresses** (one socket each), never
 *    0.0.0.0 — so a phone on hotel Wi-Fi or mobile data exposes nothing on those interfaces.
 *    If the device has no private address the server simply does not start.
 *  - Clients outside private/CGNAT ranges are refused (defence in depth behind the bind).
 *  - An optional bearer token gates every request (`Authorization: Bearer …` for scrapers,
 *    `?token=` for browsers); compared in constant time.
 *  - Request line and headers are size-capped; connections are handled on a small pool with a
 *    short socket timeout, so one slow client cannot stall the others.
 *  - Read-only: GET only, serves no files, exposes no controls.
 */
@Singleton
class PrometheusServer @Inject constructor(
    private val repository: MinerRepository,
    private val networkInspector: NetworkInspector,
) {
    private class Listener(val address: String, val socket: ServerSocket, val thread: Thread)

    @Volatile private var listeners: List<Listener> = emptyList()
    @Volatile private var pool: ExecutorService? = null
    @Volatile private var token: String = ""
    @Volatile var runningPort: Int = -1
        private set

    /** Addresses currently bound (for UI/diagnostics). */
    val boundAddresses: List<String> get() = listeners.map { it.address }

    /**
     * Reconcile the server with settings. Called from every poll cycle, so a change of network
     * (new LAN address, Tailscale up/down) re-binds within one poll interval.
     */
    @Synchronized
    fun apply(enabled: Boolean, port: Int, token: String = "") {
        this.token = token.trim()
        if (!enabled) { stop(); return }
        val wanted = networkInspector.localBindAddresses().sorted()
        if (runningPort == port && listeners.isNotEmpty() && listeners.map { it.address }.sorted() == wanted) return
        stop()
        if (wanted.isNotEmpty()) start(port, wanted)
    }

    @Synchronized
    private fun start(port: Int, addresses: List<String>) {
        val exec = Executors.newFixedThreadPool(WORKERS) { r ->
            Thread(r, "prometheus-worker").apply { isDaemon = true }
        }
        pool = exec
        val bound = addresses.mapNotNull { addr ->
            runCatching {
                val s = ServerSocket(port, BACKLOG, InetAddress.getByName(addr))
                val t = thread(name = "prometheus-accept-$addr", isDaemon = true) {
                    while (!s.isClosed) {
                        val socket = runCatching { s.accept() }.getOrNull() ?: break
                        runCatching { exec.execute { serve(socket) } }.onFailure { runCatching { socket.close() } }
                    }
                }
                Listener(addr, s, t)
            }.getOrNull()
        }
        listeners = bound
        runningPort = if (bound.isNotEmpty()) port else -1
        if (bound.isEmpty()) { exec.shutdownNow(); pool = null }
    }

    @Synchronized
    fun stop() {
        listeners.forEach { runCatching { it.socket.close() } }
        listeners = emptyList()
        pool?.shutdownNow()
        pool = null
        runningPort = -1
    }

    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = SOCKET_TIMEOUT_MS
            handle(socket)
        } catch (_: Exception) {
            // best-effort local endpoint; a bad client never affects polling
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun handle(socket: Socket) {
        val out = socket.getOutputStream()
        val remote = socket.inetAddress?.hostAddress ?: ""
        if (!MinerHostValidator.isAllowedIp(remote)) {
            respond(out, "403 Forbidden", TEXT, "Forbidden.\n")
            return
        }
        val req = HttpRequestReader.read(socket.getInputStream(), MAX_LINE, MAX_HEADERS)
        when {
            req == null -> respond(out, "431 Request Header Fields Too Large", TEXT, "Request too large.\n")
            req.method != "GET" -> respond(out, "405 Method Not Allowed", TEXT, "GET only.\n")
            !authorized(req.authorization, req.query) -> respond(
                out, "401 Unauthorized", TEXT, "Unauthorized.\n",
                extraHeaders = "WWW-Authenticate: Bearer realm=\"hi3-hashkit\"\r\n",
            )
            else -> route(out, req.path)
        }
    }

    private fun route(out: OutputStream, path: String) {
        when (path) {
            "/metrics" -> {
                val body = runCatching { runBlocking { MetricsFormatter.render(currentMiners()) } }
                    .getOrDefault("# metrics unavailable\n")
                respond(out, "200 OK", "text/plain; version=0.0.4; charset=utf-8", body)
            }
            "/", "/dashboard", "/index.html" -> {
                val body = runCatching { runBlocking { HtmlDashboard.render(currentMiners()) } }
                    .getOrDefault("<html><body>dashboard unavailable</body></html>")
                respond(out, "200 OK", "text/html; charset=utf-8", body)
            }
            else -> respond(out, "404 Not Found", TEXT, "Not found. Try GET / (dashboard) or /metrics\n")
        }
    }

    private fun authorized(authorizationHeader: String?, query: String): Boolean =
        PrometheusAuth.authorized(authorizationHeader, query, token)

    private suspend fun currentMiners() =
        repository.observeMinerEntities().first().map { repository.toDomain(it, Instant.now()) }

    private fun respond(
        out: OutputStream,
        status: String,
        contentType: String,
        body: String,
        extraHeaders: String = "",
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append(extraHeaders)
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private companion object {
        const val TEXT = "text/plain; charset=utf-8"
        const val WORKERS = 4
        const val BACKLOG = 16
        const val SOCKET_TIMEOUT_MS = 3_000
        const val MAX_LINE = 8 * 1024
        const val MAX_HEADERS = 64
    }
}

/** Bearer-token check for the embedded server; a plain object so it unit-tests without Android. */
internal object PrometheusAuth {
    /** No token configured → open on the private interfaces; otherwise header or query must match. */
    fun authorized(authorizationHeader: String?, query: String, expected: String): Boolean {
        if (expected.isEmpty()) return true
        val fromHeader = authorizationHeader?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring("Bearer ".length)?.trim()
        val fromQuery = query.split('&').firstOrNull { it.startsWith("token=") }
            ?.substringAfter('=')?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        return listOfNotNull(fromHeader, fromQuery).any { constantTimeEquals(it, expected) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        java.security.MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}

/** Minimal, size-capped HTTP/1.1 request-head parser for the embedded server. */
internal object HttpRequestReader {
    class Req(val method: String?, val path: String, val query: String, val authorization: String?)

    /** Request line + headers; null when a line or the header count exceeds the caps (or EOF). */
    fun read(input: InputStream, maxLine: Int, maxHeaders: Int): Req? {
        val requestLine = readLine(input, maxLine) ?: return null
        var authorization: String? = null
        var headerLines = 0
        while (true) {
            val line = readLine(input, maxLine) ?: return null
            if (line.isEmpty()) break
            if (++headerLines > maxHeaders) return null
            if (line.startsWith("authorization:", ignoreCase = true)) authorization = line.substringAfter(':').trim()
        }
        val parts = requestLine.split(" ")
        val target = parts.getOrNull(1) ?: ""
        return Req(parts.getOrNull(0), target.substringBefore('?'), target.substringAfter('?', ""), authorization)
    }

    /** One CRLF/LF-terminated line as ISO-8859-1, or null when it exceeds [max] bytes or EOF hits first. */
    private fun readLine(input: InputStream, max: Int): String? {
        val buf = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString("ISO-8859-1").trimEnd('\r')
            if (b == '\n'.code) return buf.toString("ISO-8859-1").trimEnd('\r')
            if (buf.size() >= max) return null
            buf.write(b)
        }
    }
}
