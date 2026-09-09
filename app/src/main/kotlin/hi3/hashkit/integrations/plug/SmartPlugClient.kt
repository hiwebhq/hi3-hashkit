package hi3.hashkit.integrations.plug

import hi3.hashkit.discovery.MinerHostValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/** Local-network smart plugs used for the over-temp safety cutoff. */
enum class PlugType(val label: String) {
    TASMOTA("Tasmota"),
    SHELLY("Shelly"),
    KASA("Kasa / TP-Link"),
    WEBHOOK("Generic webhook");

    companion object {
        fun fromName(name: String?): PlugType? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Switches a local smart plug on or off over its LAN API — no cloud. Verified command
 * shapes:
 *  - Tasmota: GET http://<host>/cm?cmnd=Power%20Off | Power%20On
 *  - Shelly:  GET /rpc/Switch.Set?id=0&on=false (Gen2), falling back to /relay/0?turn=off (Gen1)
 *  - Kasa:    TCP :9999 with the autokey-XOR-"encrypted" set_relay_state JSON
 *  - Webhook: GET the user-supplied on/off URL
 *
 * Only private LAN / Tailscale hosts are allowed. The safety monitor calls [turnOff] only;
 * turning a miner back on is always a manual action.
 */
@Singleton
class SmartPlugClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    data class Plug(
        val type: PlugType,
        val host: String?,
        val onUrl: String?,
        val offUrl: String?,
    )

    suspend fun turnOff(plug: Plug): Boolean = switch(plug, on = false)
    suspend fun turnOn(plug: Plug): Boolean = switch(plug, on = true)

    private suspend fun switch(plug: Plug, on: Boolean): Boolean = withContext(Dispatchers.IO) {
        when (plug.type) {
            PlugType.WEBHOOK -> {
                val url = (if (on) plug.onUrl else plug.offUrl)?.trim().orEmpty()
                if (url.isEmpty()) false else httpGet(url)
            }
            PlugType.TASMOTA -> {
                val host = privateHost(plug.host) ?: return@withContext false
                httpGet("http://$host/cm?cmnd=Power%20${if (on) "On" else "Off"}")
            }
            PlugType.SHELLY -> {
                val host = privateHost(plug.host) ?: return@withContext false
                // Gen2 RPC first; fall back to Gen1 relay endpoint.
                httpGet("http://$host/rpc/Switch.Set?id=0&on=$on") ||
                    httpGet("http://$host/relay/0?turn=${if (on) "on" else "off"}")
            }
            PlugType.KASA -> {
                val host = privateHost(plug.host) ?: return@withContext false
                kasaSetRelay(host, on)
            }
        }
    }

    private fun privateHost(host: String?): String? {
        val h = host?.trim()?.substringBefore(':')?.takeIf { it.isNotEmpty() } ?: return null
        // Cut power only toward private/Tailscale addresses — never a public host.
        return if (MinerHostValidator.resolvesToAllowed(h)) h else null
    }

    private fun httpGet(url: String): Boolean = runCatching {
        okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private fun httpGetBody(url: String): String? = runCatching {
        okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    }.getOrNull()

    /**
     * Read instantaneous active power (watts) from a metering plug, or null if the plug
     * doesn't meter / isn't reachable. Verified endpoints:
     *  - Tasmota:  GET /cm?cmnd=Status%208  -> StatusSNS.ENERGY.Power
     *  - Shelly:   GET /rpc/Switch.GetStatus?id=0 (Gen2, "apower"), else /meter/0 ("power")
     *  - Kasa:     emeter get_realtime -> power_mw / power (only energy-monitoring models)
     * Only private/Tailscale hosts are contacted.
     */
    suspend fun readPowerW(plug: Plug): Double? = withContext(Dispatchers.IO) {
        when (plug.type) {
            PlugType.WEBHOOK -> null
            PlugType.TASMOTA -> {
                val host = privateHost(plug.host) ?: return@withContext null
                httpGetBody("http://$host/cm?cmnd=Status%208")
                    ?.let { Regex("\"Power\"\\s*:\\s*(-?[0-9.]+)").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
            }
            PlugType.SHELLY -> {
                val host = privateHost(plug.host) ?: return@withContext null
                httpGetBody("http://$host/rpc/Switch.GetStatus?id=0")
                    ?.let { Regex("\"apower\"\\s*:\\s*(-?[0-9.]+)").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
                    ?: httpGetBody("http://$host/meter/0")
                        ?.let { Regex("\"power\"\\s*:\\s*(-?[0-9.]+)").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
            }
            PlugType.KASA -> {
                val host = privateHost(plug.host) ?: return@withContext null
                kasaReadPower(host)
            }
        }?.takeIf { it >= 0.0 }
    }

    /** Kasa emeter realtime read; returns watts (power_mw/1000 or power), or null. */
    private fun kasaReadPower(host: String): Double? = runCatching {
        val reply = kasaExchange(host, """{"emeter":{"get_realtime":{}}}""") ?: return null
        Regex("\"power_mw\"\\s*:\\s*([0-9.]+)").find(reply)?.groupValues?.get(1)?.toDoubleOrNull()?.div(1000.0)
            ?: Regex("\"power\"\\s*:\\s*([0-9.]+)").find(reply)?.groupValues?.get(1)?.toDoubleOrNull()
    }.getOrNull()

    /** Kasa local protocol: 4-byte length prefix + autokey-XOR-encrypted JSON on TCP 9999. */
    private fun kasaSetRelay(host: String, on: Boolean): Boolean = runCatching {
        val payload = """{"system":{"set_relay_state":{"state":${if (on) 1 else 0}}}}"""
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, 9999), 3000)
            socket.soTimeout = 3000
            socket.getOutputStream().apply { write(kasaEncrypt(payload)); flush() }
            // Read the length-prefixed reply so the plug commits the command.
            val din = DataInputStream(socket.getInputStream())
            val len = din.readInt()
            if (len in 1..4096) { val buf = ByteArray(len); din.readFully(buf) }
        }
        true
    }.getOrDefault(false)

    /** Send an encrypted Kasa command and return the decrypted JSON reply, or null. */
    private fun kasaExchange(host: String, payload: String): String? = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, 9999), 3000)
            socket.soTimeout = 3000
            socket.getOutputStream().apply { write(kasaEncrypt(payload)); flush() }
            val din = DataInputStream(socket.getInputStream())
            val len = din.readInt()
            if (len !in 1..8192) return null
            val buf = ByteArray(len); din.readFully(buf)
            kasaDecrypt(buf)
        }
    }.getOrNull()

    private fun kasaDecrypt(bytes: ByteArray): String {
        val out = ByteArray(bytes.size)
        var key = 171
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            out[i] = (key xor b).toByte()
            key = b
        }
        return String(out, Charsets.UTF_8)
    }

    private fun kasaEncrypt(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val out = ByteArray(bytes.size + 4)
        // 4-byte big-endian length header.
        out[0] = (bytes.size ushr 24).toByte(); out[1] = (bytes.size ushr 16).toByte()
        out[2] = (bytes.size ushr 8).toByte(); out[3] = bytes.size.toByte()
        var key = 171
        for (i in bytes.indices) {
            val enc = key xor bytes[i].toInt()
            out[i + 4] = enc.toByte()
            key = enc and 0xFF
        }
        return out
    }
}
