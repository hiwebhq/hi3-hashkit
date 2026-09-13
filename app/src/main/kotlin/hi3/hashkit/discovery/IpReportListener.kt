package hi3.hashkit.discovery

import android.content.Context
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/** One IP Report button press heard from a miner: the "IP,MAC" broadcast, parsed. */
data class IpReport(val ip: String, val mac: String)

private const val OCTET_MAX = 255
private const val IPV4_OCTETS = 4
private val MAC_RE = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")

/**
 * Parse an Antminer IP Report datagram payload, e.g. "192.168.1.100,AA:BB:CC:DD:EE:FF".
 * Trailing fields (some firmwares append extras) are tolerated; malformed IP or MAC is not.
 */
fun parseIpReport(payload: String): IpReport? {
    val parts = payload.trim { it <= ' ' }.split(',')
    if (parts.size < 2) return null
    val ip = parts[0].trim()
    val mac = parts[1].trim().uppercase()
    val octets = ip.split('.')
    val ipOk = octets.size == IPV4_OCTETS &&
        octets.all { val v = it.toIntOrNull(); v != null && v in 0..OCTET_MAX }
    return if (ipOk && MAC_RE.matches(mac)) IpReport(ip, mac) else null
}

/**
 * Listens for Bitmain-style IP Report broadcasts (UDP :14235). The miner's button daemon
 * broadcasts "IP,MAC" every few seconds for up to ~2 minutes until someone echoes the
 * datagram back to it; that ack stops the retry loop and blinks the miner's green LED —
 * the walker's physical confirmation during a Site Map capture.
 *
 * Covers stock Antminer firmware (S9→S21 era) plus Braiins OS and LuxOS on Antminer
 * hardware, which keep the same mechanism. Purely LAN-scope: broadcasts never cross
 * routers or the Tailscale subnet router, so capture only works on the miners' wifi.
 */
@Singleton
class IpReportListener @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Cold flow of parsed reports; the socket opens on collect and closes on cancel.
     * When [sendAck] is set each datagram is echoed back to its sender.
     */
    fun reports(sendAck: Boolean = true): Flow<IpReport> = callbackFlow {
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(ANTMINER_PORT))
        }
        // Some wifi drivers drop broadcast/multicast frames unless a multicast lock is held.
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("hashkit-ip-report")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        val reader = thread(name = "ip-report-$ANTMINER_PORT") {
            val buf = ByteArray(BUFFER_BYTES)
            var open = true
            while (open && !socket.isClosed) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    socket.receive(pkt)
                    val text = String(pkt.data, pkt.offset, pkt.length, Charsets.US_ASCII)
                    val report = parseIpReport(text)
                    if (report != null) {
                        if (sendAck) {
                            runCatching {
                                socket.send(DatagramPacket(pkt.data, pkt.offset, pkt.length, pkt.address, pkt.port))
                            }
                        }
                        trySend(report)
                    }
                } catch (_: SocketException) {
                    open = false // socket closed — collector cancelled
                } catch (_: IOException) {
                    // transient receive error; keep listening
                }
            }
        }
        awaitClose {
            runCatching { socket.close() }
            runCatching { lock?.release() }
            runCatching { reader.join(THREAD_JOIN_MS) }
        }
    }

    private companion object {
        const val ANTMINER_PORT = 14235
        const val BUFFER_BYTES = 512
        const val THREAD_JOIN_MS = 1_000L
    }
}
