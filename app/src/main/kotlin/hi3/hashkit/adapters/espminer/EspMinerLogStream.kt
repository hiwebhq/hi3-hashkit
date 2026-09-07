package hi3.hashkit.adapters.espminer

import hi3.hashkit.discovery.MinerHostValidator
import hi3.hashkit.domain.adapter.MinerHost
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live log streaming from ESP-Miner-family firmware over the `/api/ws` WebSocket,
 * verified live against official AxeOS v2.14/v2.15 and NerdQAxe v1.1 (all answer
 * 101 and stream ESP-IDF log lines). Read-only: the socket sends nothing.
 *
 * Lines are ANSI-stripped and wallet-like tokens are redacted before they leave this
 * class, so credentials never reach the UI or a screenshot.
 */
@Singleton
class EspMinerLogStream @Inject constructor(
    private val client: OkHttpClient,
) {
    sealed interface LogEvent {
        data class Line(val text: String) : LogEvent
        data class Closed(val reason: String) : LogEvent
    }

    fun stream(host: MinerHost): Flow<LogEvent> = callbackFlow {
        if (!MinerHostValidator.resolvesToAllowed(host.host)) {
            trySendBlocking(LogEvent.Closed("Refused: ${host.host} is not a private/Tailscale address"))
            close()
            return@callbackFlow
        }
        val request = Request.Builder()
            .url("ws://${host.host}:${host.port}/api/ws")
            .build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                text.split('\n')
                    .map { LogRedactor.clean(it) }
                    .filter { it.isNotBlank() }
                    .forEach { trySendBlocking(LogEvent.Line(it)) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySendBlocking(LogEvent.Closed(t.message ?: "Connection lost"))
                close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySendBlocking(LogEvent.Closed("Closed by miner"))
                close()
            }
        })
        awaitClose { socket.cancel() }
    }
}

/** ANSI stripping + credential redaction for miner log lines. Pure and unit-tested. */
object LogRedactor {

    private val ansi = Regex("\\u001B?\\[[0-9;]*[A-Za-z]")

    // Bech32 and base58 Bitcoin addresses (20+ chars) and worker suffixes after them.
    private val wallet = Regex("\\b(bc1[a-z0-9]{20,}|[13][a-km-zA-HJ-NP-Z1-9]{24,})(\\.[\\w-]+)?")

    fun clean(line: String): String =
        wallet.replace(ansi.replace(line, "").trimEnd()) { m ->
            "[wallet]" + (m.groupValues[2].takeIf { it.isNotEmpty() } ?: "")
        }
}
