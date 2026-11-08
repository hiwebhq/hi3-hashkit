package hi3.hashkit.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
    @ApplicationContext private val context: Context,
) {
    fun localPrivateAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nic -> nic.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .filter { MinerHostValidator.isAllowedIp(it) && !it.startsWith("100.") && !it.startsWith("169.254.") }
    }.getOrDefault(emptyList())

    /**
     * Addresses a local listener (the optional web dashboard / Prometheus endpoint) may bind to.
     *
     * Chosen by **transport**, not just by address range: Wi-Fi and Ethernet interfaces, plus a
     * VPN interface (Tailscale) when it carries a CGNAT 100.64/10 address. A cellular interface
     * is never included even though carriers hand out 100.64/10 addresses — those are shared
     * with every other subscriber behind the same CGNAT, not with the user's LAN. Link-local,
     * loopback and public addresses are never bound.
     */
    fun localBindAddresses(): List<String> = runCatching {
        val excluded = cellularInterfaceNames()
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && it.name !in excluded }
            .flatMap { nic -> nic.inetAddresses.toList().map { nic.name to it } }
            .mapNotNull { (nic, addr) -> (addr as? Inet4Address)?.hostAddress?.let { nic to it } }
            .filter { (nic, ip) ->
                MinerHostValidator.isAllowedIp(ip) && !ip.startsWith("169.254.") && !ip.startsWith("127.") &&
                    // CGNAT only on a VPN-style interface (Tailscale), never on an unknown one.
                    (!ip.startsWith("100.") || isVpnInterface(nic))
            }
            .map { it.second }
            .distinct()
    }.getOrDefault(emptyList())

    /** Interface names of every network whose transport is cellular (e.g. rmnet0, ccmni0). */
    private fun cellularInterfaceNames(): Set<String> = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return emptySet()
        cm.allNetworks.mapNotNull { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@mapNotNull null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return@mapNotNull null
            cm.getLinkProperties(n)?.interfaceName
        }.toSet()
    }.getOrDefault(emptySet())

    private fun isVpnInterface(name: String): Boolean {
        if (name.startsWith("tun") || name.startsWith("tailscale") || name.startsWith("wg")) return true
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            cm.allNetworks.any { n ->
                cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
                    cm.getLinkProperties(n)?.interfaceName == name
            }
        }.getOrDefault(false)
    }

    /** Default scan target: the /24 of the first LAN address, if any. */
    fun defaultScanCidr(): SubnetUtils.Cidr? =
        localPrivateAddresses().firstOrNull()?.let { SubnetUtils.slash24Of(it) }
}
