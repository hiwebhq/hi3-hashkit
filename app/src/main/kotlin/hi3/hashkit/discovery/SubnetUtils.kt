package hi3.hashkit.discovery

/** CIDR expansion with hard safety bounds for discovery scans. */
object SubnetUtils {

    /** Largest scan permitted by default: /22 = 1022 hosts. */
    const val DEFAULT_MAX_PREFIX = 22

    data class Cidr(val baseIp: String, val prefix: Int)

    fun parseCidr(input: String): Cidr? {
        val parts = input.trim().split("/")
        if (parts.size != 2) return null
        val prefix = parts[1].toIntOrNull() ?: return null
        if (prefix !in 8..32) return null
        if (!MinerHostValidator.isAllowedIp(parts[0])) return null
        return Cidr(parts[0], prefix)
    }

    /**
     * Expand a CIDR into scannable host addresses (network/broadcast excluded for <31).
     * Returns null when the range exceeds [maxPrefix] — caller must ask the user to
     * explicitly override rather than silently scanning a huge range.
     */
    fun expand(cidr: Cidr, maxPrefix: Int = DEFAULT_MAX_PREFIX): List<String>? {
        if (cidr.prefix < maxPrefix) return null
        val base = ipToLong(cidr.baseIp) ?: return null
        val hostBits = 32 - cidr.prefix
        val network = base and (0xFFFFFFFFL shl hostBits)
        val count = 1L shl hostBits
        val range = when {
            count <= 2 -> network until network + count
            else -> (network + 1) until (network + count - 1)
        }
        return range.map { longToIp(it) }.filter { MinerHostValidator.isAllowedIp(it) }
    }

    /** The /24 containing [ip] — the default scan for the connected network. */
    fun slash24Of(ip: String): Cidr? {
        if (!MinerHostValidator.isAllowedIp(ip)) return null
        val octets = ip.split(".")
        return Cidr("${octets[0]}.${octets[1]}.${octets[2]}.0", 24)
    }

    private fun ipToLong(ip: String): Long? {
        val o = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (o.size != 4 || o.any { it !in 0..255 }) return null
        return (o[0].toLong() shl 24) or (o[1].toLong() shl 16) or (o[2].toLong() shl 8) or o[3].toLong()
    }

    private fun longToIp(v: Long): String =
        "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"
}
