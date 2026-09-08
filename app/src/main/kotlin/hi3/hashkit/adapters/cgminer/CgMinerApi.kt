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

    suspend fun query(host: String, port: Int, command: String, parameter: String? = null): CgResult {
        // ascset etc. take a "parameter" field; JSON-escape it minimally.
        val payload = if (parameter == null) {
            """{"command":"$command"}"""
        } else {
            val safe = parameter.replace("\\", "\\\\").replace("\"", "\\\"")
            """{"command":"$command","parameter":"$safe"}"""
        }
        return exchange(host, port, payload)
    }

    /**
     * WhatsMiner (MicroBT/BTMiner) variant: its read API on 4028 uses `{"cmd":"<x>"}`
     * (not cgminer's `{"command":...}`). Read commands (summary/pools/devs/get_miner_info/
     * get_psu/status/version) are open; write commands need an encrypted token and are not
     * sent here.
     */
    suspend fun queryCmd(host: String, port: Int, cmd: String): CgResult =
        exchange(host, port, """{"cmd":"$cmd"}""")

    private suspend fun exchange(host: String, port: Int, payload: String): CgResult =
        withContext(Dispatchers.IO) {
            if (!MinerHostValidator.resolvesToAllowed(host)) {
                return@withContext CgResult(null, "Refused: $host is not a private/Tailscale address")
            }
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = READ_TIMEOUT_MS
                    socket.getOutputStream().write(payload.toByteArray())
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
