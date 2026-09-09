package hi3.hashkit.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers miners advertising themselves over mDNS/DNS-SD on the local network, using
 * Android's [NsdManager]. Browses the common HTTP service type (miners like AxeOS serve a
 * web UI), resolves each to an address, and returns the private/Tailscale IPs — which the
 * caller then probes with the normal adapter pipeline. Best-effort and time-boxed; requires
 * no permissions beyond the LAN access the app already uses.
 */
@Singleton
class NsdDiscoverer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Service types worth browsing for miner web UIs. */
    private val serviceTypes = listOf("_http._tcp.")

    /** Discover for up to [timeoutMs], returning distinct allowed IPs found. */
    suspend fun discover(timeoutMs: Long = 5000): List<String> {
        val found = Collections.synchronizedSet(linkedSetOf<String>())
        withTimeoutOrNull(timeoutMs) {
            serviceTypes.forEach { type -> browse(type, found) }
        }
        return found.toList()
    }

    private suspend fun browse(type: String, sink: MutableSet<String>) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val flow = callbackFlow<NsdServiceInfo> {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) { trySend(service) }
                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) { close() }
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
            }
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
                .onFailure { close() }
            awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
        }
        // Resolve each discovered service to an address, sequentially (NsdManager allows
        // only one resolve at a time on older APIs).
        flow.toList().forEach { service ->
            resolve(nsd, service)?.let { ip ->
                if (MinerHostValidator.isAllowedIp(ip)) sink.add(ip)
            }
        }
    }

    private suspend fun resolve(nsd: NsdManager, service: NsdServiceInfo): String? =
        withTimeoutOrNull(2000) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                val listener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resumeWith(Result.success(null))
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val ip = serviceInfo.host?.hostAddress
                        if (cont.isActive) cont.resumeWith(Result.success(ip))
                    }
                }
                runCatching { nsd.resolveService(service, listener) }
                    .onFailure { if (cont.isActive) cont.resumeWith(Result.success(null)) }
            }
        }
}
