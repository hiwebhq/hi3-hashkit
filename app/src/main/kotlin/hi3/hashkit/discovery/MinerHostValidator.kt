package hi3.hashkit.discovery

import java.net.Inet4Address
import java.net.InetAddress

/**
 * The app's enforceable network boundary: miner connections are only permitted to
 * private (RFC 1918), link-local, loopback, or CGNAT (RFC 6598, which covers
 * Tailscale's 100.64.0.0/10) addresses. Public addresses are refused — the app
 * never scans or polls the public Internet.
 *
 * Two layers enforce it:
 *  - callers pre-check with [resolvesToAllowed] / [resolveAllowed] and connect raw sockets to
 *    the vetted [InetAddress] (never to the hostname, which would resolve a second time);
 *  - every cleartext OkHttp request is re-checked against the address the socket actually
 *    connected to ([PrivateCleartextInterceptor]), which closes the DNS-rebinding /
 *    multi-record / redirect gap for hostnames.
 */
object MinerHostValidator {

    private val dottedQuad = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    /** Strict dotted-quad parse: four decimal octets 0..255, no signs, no hex, no shorthand. */
    fun parseIpv4(text: String): IntArray? {
        val m = dottedQuad.matchEntire(text.trim()) ?: return null
        // "010" is octal to inet_pton and a hostname to Android — never an allowed literal.
        val octets = m.groupValues.drop(1).map { s -> if (s.length > 1 && s[0] == '0') return null else s.toInt() }
        return if (octets.all { it in 0..255 }) octets.toIntArray() else null
    }

    fun isAllowedIp(ip: String): Boolean {
        val octets = parseIpv4(ip) ?: return false
        return isAllowedOctets(octets[0], octets[1])
    }

    /** Same policy on a resolved address: IPv4 only (IPv6 fails closed, there are no v6 miners here). */
    fun isAllowedAddress(address: InetAddress): Boolean {
        val v4 = address as? Inet4Address ?: return false
        val b = v4.address
        return isAllowedOctets(b[0].toInt() and OCTET_MASK, b[1].toInt() and OCTET_MASK)
    }

    private const val OCTET_MASK = 0xFF

    private fun isAllowedOctets(a: Int, b: Int): Boolean = when {
        a == 10 -> true                       // 10.0.0.0/8
        a == 172 && b in 16..31 -> true       // 172.16.0.0/12
        a == 192 && b == 168 -> true          // 192.168.0.0/16
        a == 100 && b in 64..127 -> true      // 100.64.0.0/10 CGNAT (Tailscale)
        a == 169 && b == 254 -> true          // link-local
        a == 127 -> true                      // loopback (tests)
        else -> false
    }

    /**
     * Resolve [host] and return an allowed address to connect to, or null when it is public,
     * unresolvable or IPv6-only. Every returned record must pass — one private A record next
     * to a public one is treated as hostile (DNS rebinding / split answers), not as "allowed".
     * IP literals short-circuit without a lookup. Blocking: call off the main thread.
     */
    fun resolveAllowed(host: String): InetAddress? {
        val h = host.trim()
        if (h.isEmpty()) return null // getAllByName("") would answer with loopback
        if (parseIpv4(h) != null) return if (isAllowedIp(h)) InetAddress.getByName(h) else null
        // Ignore AAAA records (we never connect over v6) but refuse if any A record is public.
        val v4 = runCatching { InetAddress.getAllByName(h).toList() }.getOrNull().orEmpty()
            .filterIsInstance<Inet4Address>()
        return if (v4.isNotEmpty() && v4.all { isAllowedAddress(it) }) v4.first() else null
    }

    /**
     * Hostname entries (e.g. Tailscale MagicDNS names) are allowed only if they
     * resolve exclusively to allowed addresses at connect time.
     */
    fun resolvesToAllowed(host: String): Boolean = resolveAllowed(host) != null
}
