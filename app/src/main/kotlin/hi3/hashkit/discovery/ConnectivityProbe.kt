package hi3.hashkit.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uplink diagnostics for the flow view.
 *
 * Internet up/down is read from Android's own [ConnectivityManager] validated-network
 * capability — the OS already determined this, so it costs no traffic of our own.
 * Latency is a TCP-connect probe to the stratum host the miners already submit to
 * (local or their configured pool), so no new third-party endpoint is contacted.
 */
@Singleton
class ConnectivityProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** True when the active network has validated internet access (no traffic sent). */
    fun internetValidated(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** TCP-connect latency to host:port in ms, or null if unreachable within [timeoutMs]. */
    suspend fun tcpLatencyMs(host: String, port: Int, timeoutMs: Int = 4000): Long? =
        withContext(Dispatchers.IO) {
            if (host.isBlank() || port !in 1..65535) return@withContext null
            runCatching {
                val start = System.nanoTime()
                Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                (System.nanoTime() - start) / 1_000_000
            }.getOrNull()
        }
}
