package hi3.hashkit.integrations.poolspeed

import java.net.Inet4Address
import java.net.InetAddress

/**
 * Policy for TCP probes toward stratum pool endpoints. Unlike miners, pools are legitimately
 * public, so the private-address rule does not apply — but the host:port usually comes from a
 * *miner's* telemetry (`stratumURL`), i.e. from a device an attacker may control. The probe
 * must therefore never become a general-purpose port scanner / line injector:
 *
 *  - only the well-known stratum ports are probed: 443 (stratum over TLS/443 is common) or
 *    any unprivileged port ≥ 1024 — never SSH, SMTP, printers, routers, etc.;
 *  - the host is resolved once and the vetted address is what the socket connects to;
 *  - the single response line read back is capped at [MAX_LINE_BYTES].
 */
object StratumTarget {

    const val MAX_LINE_BYTES = 4 * 1024
    private const val HTTPS_PORT = 443
    private const val FIRST_UNPRIVILEGED_PORT = 1024
    private const val MAX_PORT = 65535

    fun isAllowedPort(port: Int): Boolean = port == HTTPS_PORT || port in FIRST_UNPRIVILEGED_PORT..MAX_PORT

    /** First IPv4 address for [host] (literal or name), or null when unresolvable / IPv6-only. */
    fun resolve(host: String): InetAddress? {
        val h = host.trim()
        if (h.isEmpty()) return null
        return runCatching { InetAddress.getAllByName(h).filterIsInstance<Inet4Address>().firstOrNull() }
            .getOrNull()
    }

    /** Read one `\n`-terminated line (ISO-8859-1 bytes as chars), or null when it exceeds [max] bytes or EOF. */
    fun readLineCapped(input: java.io.InputStream, max: Int = MAX_LINE_BYTES): String? {
        val buf = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString("ISO-8859-1")
            if (b == '\n'.code) return buf.toString("ISO-8859-1")
            if (buf.size() >= max) return null
            buf.write(b)
        }
    }
}
