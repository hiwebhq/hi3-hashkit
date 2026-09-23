package hi3.hashkit.integrations.plug

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds Kasa/Tapo plugs on the local network with TP-Link's two broadcast discovery
 * protocols (both answered by real plugs on 2026-09-22: 12× KP125M on the new port, one
 * KP115 on both):
 *  - UDP 20002: 16-byte magic probe; newer devices answer with a 16-byte header + JSON
 *    (`result.device_model`, `device_type`, `mac`, `mgt_encrypt_schm.encrypt_type`)
 *  - UDP 9999: autokey-XOR `get_sysinfo`; legacy devices answer with model + alias
 * Nothing leaves the LAN: both are subnet broadcasts.
 */
@Singleton
class KasaDiscovery @Inject constructor() {

    data class Found(
        val ip: String,
        val model: String?,
        val alias: String?,
        val mac: String?,
        /** True when the device speaks KLAP (needs the TP-Link account); false = legacy 9999. */
        val klap: Boolean,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun discover(timeoutMs: Int = DEFAULT_TIMEOUT_MS): List<Found> = withContext(Dispatchers.IO) {
        val byIp = linkedMapOf<String, Found>()
        listen(NEW_PORT, NEW_PROBE, timeoutMs) { ip, bytes -> parseNew(ip, bytes)?.let { byIp[ip] = it } }
        listen(LEGACY_PORT, legacyEncrypt(LEGACY_PROBE), timeoutMs) { ip, bytes ->
            parseLegacy(ip, bytes)?.let { found ->
                byIp[ip] = byIp[ip]?.copy(alias = found.alias, klap = false) ?: found
            }
        }
        byIp.values.sortedBy { it.ip.sortKey() }
    }

    private fun listen(port: Int, probe: ByteArray, timeoutMs: Int, onReply: (String, ByteArray) -> Unit) {
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = REPLY_WAIT_MS
                socket.send(DatagramPacket(probe, probe.size, InetAddress.getByName("255.255.255.255"), port))
                val buf = ByteArray(MAX_REPLY)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    val received = try {
                        socket.receive(packet); true
                    } catch (_: SocketTimeoutException) {
                        false
                    }
                    val ip = packet.address?.hostAddress
                    if (received && ip != null) onReply(ip, packet.data.copyOf(packet.length))
                }
            }
        }
    }

    private fun parseNew(ip: String, bytes: ByteArray): Found? {
        if (bytes.size <= NEW_HEADER) return null
        val text = String(bytes, NEW_HEADER, bytes.size - NEW_HEADER)
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val r = (obj["result"] as? JsonObject) ?: obj
        val scheme = r["mgt_encrypt_schm"] as? JsonObject
        return Found(
            ip = ip,
            model = r.str("device_model"),
            alias = null, // newer plugs only reveal the nickname after the KLAP login
            mac = r.str("mac")?.replace('-', ':')?.lowercase(),
            klap = scheme?.str("encrypt_type")?.equals("KLAP", ignoreCase = true) ?: true,
        )
    }

    private fun parseLegacy(ip: String, bytes: ByteArray): Found? {
        val text = legacyDecrypt(bytes)
        val info = runCatching {
            (json.parseToJsonElement(text).jsonObject["system"] as? JsonObject)?.get("get_sysinfo") as? JsonObject
        }.getOrNull() ?: return null
        return Found(
            ip = ip,
            model = info.str("model"),
            alias = info.str("alias"),
            mac = info.str("mac")?.lowercase(),
            klap = false,
        )
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    /** Numeric sort key for dotted IPv4 so .12 lists before .106. */
    private fun String.sortKey(): Long =
        split('.').map { it.toIntOrNull() ?: 0 }.fold(0L) { acc, p -> acc * OCTET_RADIX + p }

    private fun legacyEncrypt(text: String): ByteArray {
        var key = XOR_KEY
        return text.toByteArray()
            .map { b -> (key xor b.toInt()).also { key = it and BYTE_MASK }.toByte() }
            .toByteArray()
    }

    private fun legacyDecrypt(bytes: ByteArray): String {
        var key = XOR_KEY
        val out = ByteArray(bytes.size)
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and BYTE_MASK
            out[i] = (key xor b).toByte()
            key = b
        }
        return String(out)
    }

    companion object {
        const val NEW_PORT = 20002
        const val LEGACY_PORT = 9999
        private const val NEW_HEADER = 16
        private const val DEFAULT_TIMEOUT_MS = 2500
        private const val REPLY_WAIT_MS = 500
        private const val MAX_REPLY = 4096
        private const val XOR_KEY = 171
        private const val BYTE_MASK = 0xFF
        private const val OCTET_RADIX = 256L
        private const val LEGACY_PROBE = """{"system":{"get_sysinfo":{}}}"""
        private val NEW_PROBE = byteArrayOf(
            0x02, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x46, 0x3c, 0xb5.toByte(), 0xd3.toByte(),
        )
    }
}
