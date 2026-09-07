package hi3.hashkit.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional mDNS discovery: browses HTTP services on the LAN via Android's NsdManager
 * and emits resolved private IPv4 addresses. Candidates are still probed by the
 * adapters before anything is added — mDNS only shortcuts the address search.
 */
@Singleton
class MdnsDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun discoverHttpHosts(): Flow<String> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                // Resolve each found service to an address; failures are silently skipped.
                nsd.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host
                        if (host is Inet4Address) {
                            host.hostAddress
                                ?.takeIf { MinerHostValidator.isAllowedIp(it) }
                                ?.let { trySendBlocking(it) }
                        }
                    }

                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        nsd.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(discoveryListener) }
        }
    }
}
