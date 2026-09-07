package hi3.hashkit.discovery

import java.net.InetAddress

/**
 * The app's enforceable network boundary: miner connections are only permitted to
 * private (RFC 1918), link-local, loopback, or CGNAT (RFC 6598, which covers
 * Tailscale's 100.64.0.0/10) addresses. Public addresses are refused — the app
 * never scans or polls the public Internet.
 */
object MinerHostValidator {

    fun isAllowedIp(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val (a, b) = octets
        return when {
            a == 10 -> true                       // 10.0.0.0/8
            a == 172 && b in 16..31 -> true       // 172.16.0.0/12
            a == 192 && b == 168 -> true          // 192.168.0.0/16
            a == 100 && b in 64..127 -> true      // 100.64.0.0/10 CGNAT (Tailscale)
            a == 169 && b == 254 -> true          // link-local
            a == 127 -> true                      // loopback (tests)
            else -> false
        }
    }

    /**
     * Hostname entries (e.g. Tailscale MagicDNS names) are allowed only if they
     * resolve to an allowed address at connect time.
     */
    fun resolvesToAllowed(host: String): Boolean {
        if (isAllowedIp(host)) return true
        return runCatching {
            InetAddress.getAllByName(host).any { addr ->
                addr.hostAddress?.let { isAllowedIp(it) } == true
            }
        }.getOrDefault(false)
    }
}
