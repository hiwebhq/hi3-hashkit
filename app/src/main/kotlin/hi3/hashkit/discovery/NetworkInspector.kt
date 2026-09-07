package hi3.hashkit.discovery

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the device's active private IPv4 address(es) to derive the default scan range.
 * Tailscale interfaces (CGNAT 100.64/10) are intentionally excluded from the *default*
 * scan — Tailscale hosts are added manually or via user-defined subnets.
 */
@Singleton
class NetworkInspector @Inject constructor(
    @ApplicationContext @Suppress("unused") private val context: Context,
) {
    fun localPrivateAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nic -> nic.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .filter { MinerHostValidator.isAllowedIp(it) && !it.startsWith("100.") && !it.startsWith("169.254.") }
    }.getOrDefault(emptyList())

    /** Default scan target: the /24 of the first LAN address, if any. */
    fun defaultScanCidr(): SubnetUtils.Cidr? =
        localPrivateAddresses().firstOrNull()?.let { SubnetUtils.slash24Of(it) }
}
