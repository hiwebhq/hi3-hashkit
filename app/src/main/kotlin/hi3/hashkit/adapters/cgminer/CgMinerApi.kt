package hi3.hashkit.adapters.cgminer

import hi3.hashkit.discovery.MinerHostValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared transport for the classic CGMiner TCP API on port 4028: connect, send
 * {"command":"<cmd>"}, read one JSON response, socket closes. Used by the Canaan
 * (verified: Avalon Nano 3) and Braiins OS (verified: BOSer/BMM 100) adapters.
 * Read-only commands only — this client sends no privileged commands.
 */
@Singleton
class CgMinerApi @Inject constructor() {

    class CgResult(val body: String?, val error: String?)

    suspend fun query(host: String, port: Int, command: String): CgResult =
        withContext(Dispatchers.IO) {
            if (!MinerHostValidator.resolvesToAllowed(host)) {
                return@withContext CgResult(null, "Refused: $host is not a private/Tailscale address")
            }
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = READ_TIMEOUT_MS
                    socket.getOutputStream().write("""{"command":"$command"}""".toByteArray())
                    socket.getOutputStream().flush()
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    val input = socket.getInputStream()
                    while (true) {
                        val n = runCatching { input.read(chunk) }.getOrDefault(-1)
                        if (n <= 0) break
                        buffer.write(chunk, 0, n)
                        if (buffer.size() > MAX_RESPONSE_BYTES) break
                    }
                    // Responses are NUL-terminated.
                    buffer.toString(Charsets.UTF_8.name()).trim { it == '\u0000' || it.isWhitespace() }
                }
            }.fold(
                onSuccess = { body ->
                    if (body.isEmpty()) CgResult(null, "Empty response") else CgResult(body, null)
                },
                onFailure = { CgResult(null, it.message ?: it.javaClass.simpleName) },
            )
        }

    companion object {
        const val DEFAULT_PORT = 4028
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val READ_TIMEOUT_MS = 5_000
        private const val MAX_RESPONSE_BYTES = 256 * 1024
    }
}
