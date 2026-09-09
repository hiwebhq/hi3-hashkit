package hi3.hashkit.integrations.mqtt

import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal MQTT 3.1.1 publisher (QoS 0), hand-rolled to avoid a heavy Netty-based client for
 * what is a fire-and-forget one-way publish. Connects, sends CONNECT, verifies CONNACK,
 * publishes a batch of retained messages, then DISCONNECTs. Best-effort: any failure returns
 * false rather than throwing, so a broker being down never disrupts polling.
 *
 * TLS is intentionally not implemented here — this targets a local broker (e.g. the
 * Mosquitto add-on in Home Assistant) on the LAN/tailnet. Credentials, when set, are sent in
 * the CONNECT packet per the MQTT spec.
 */
@Singleton
class MqttClient @Inject constructor() {

    data class Message(val topic: String, val payload: String, val retain: Boolean = true)

    fun publish(
        host: String,
        port: Int,
        clientId: String,
        username: String?,
        password: String?,
        messages: List<Message>,
        connectTimeoutMs: Int = 4000,
    ): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = connectTimeoutMs
            val out = socket.getOutputStream()
            val din = DataInputStream(socket.getInputStream())

            out.write(connectPacket(clientId, username, password))
            out.flush()

            // CONNACK: 0x20, len 0x02, flags, returnCode(0 = accepted).
            val type = din.read()
            if (type != 0x20) return@runCatching false
            val len = din.read()
            if (len != 2) return@runCatching false
            din.read() // session-present flag
            val returnCode = din.read()
            if (returnCode != 0) return@runCatching false

            for (m in messages) {
                out.write(publishPacket(m.topic, m.payload, m.retain))
            }
            out.flush()
            out.write(byteArrayOf(0xE0.toByte(), 0x00)) // DISCONNECT
            out.flush()
        }
        true
    }.getOrDefault(false)

    private fun connectPacket(clientId: String, username: String?, password: String?): ByteArray {
        val payload = ArrayList<Byte>()
        appendString(payload, clientId)
        var flags = 0x02 // clean session
        if (!username.isNullOrEmpty()) {
            flags = flags or 0x80
            appendString(payload, username)
            if (!password.isNullOrEmpty()) {
                flags = flags or 0x40
                appendString(payload, password)
            }
        }
        val variableHeader = ArrayList<Byte>()
        appendString(variableHeader, "MQTT")     // protocol name
        variableHeader.add(0x04)                  // protocol level 4 (3.1.1)
        variableHeader.add(flags.toByte())        // connect flags
        variableHeader.add(0x00); variableHeader.add(0x3C) // keepalive 60s

        val body = variableHeader + payload
        return byteArrayOf(0x10.toByte()) + encodeRemainingLength(body.size) + body.toByteArray()
    }

    private fun publishPacket(topic: String, payload: String, retain: Boolean): ByteArray {
        val header = ArrayList<Byte>()
        appendString(header, topic) // QoS 0 -> no packet identifier
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val body = header.toByteArray() + payloadBytes
        val fixed = (0x30 or if (retain) 0x01 else 0x00).toByte()
        return byteArrayOf(fixed) + encodeRemainingLength(body.size) + body
    }

    private fun appendString(out: ArrayList<Byte>, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        out.add((bytes.size ushr 8).toByte())
        out.add((bytes.size and 0xFF).toByte())
        bytes.forEach { out.add(it) }
    }

    private fun OutputStream.write(bytes: ByteArray) = write(bytes, 0, bytes.size)

    companion object {
        /** MQTT variable-length "remaining length" encoding (1–4 bytes). */
        fun encodeRemainingLength(length: Int): ByteArray {
            var x = length
            val out = ArrayList<Byte>()
            do {
                var b = x % 128
                x /= 128
                if (x > 0) b = b or 128
                out.add(b.toByte())
            } while (x > 0)
            return out.toByteArray()
        }
    }
}
