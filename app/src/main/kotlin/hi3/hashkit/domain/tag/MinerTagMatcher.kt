package hi3.hashkit.domain.tag

/**
 * Pure resolution of a [MinerTag] to one of the known miners. Match priority is MAC → IP/host →
 * name; a bare legacy value is matched leniently against id / name / host / MAC / serial.
 */
object MinerTagMatcher {

    data class Candidate(
        val id: Long,
        val name: String?,
        val host: String?,
        val mac: String?,
        val serial: String?,
    )

    fun match(tag: MinerTag, candidates: List<Candidate>): Candidate? {
        MinerTag.normalizeMac(tag.mac)?.let { m ->
            candidates.firstOrNull { MinerTag.normalizeMac(it.mac) == m }?.let { return it }
        }
        tag.ip?.let { ip ->
            candidates.firstOrNull { it.host.equals(ip, ignoreCase = true) }?.let { return it }
        }
        tag.name?.let { n ->
            candidates.firstOrNull { it.name.equals(n, ignoreCase = true) }?.let { return it }
        }
        tag.rawValue?.let { return matchRaw(it, candidates) }
        return null
    }

    private fun matchRaw(raw: String, candidates: List<Candidate>): Candidate? {
        val key = raw.removePrefix("hi3miner:").removePrefix("hi3:").trim()
        val normMac = MinerTag.normalizeMac(key)
        return candidates.firstOrNull { c ->
            key.toLongOrNull()?.let { it == c.id } == true ||
                c.name.equals(key, ignoreCase = true) ||
                c.host.equals(key, ignoreCase = true) ||
                (normMac != null && MinerTag.normalizeMac(c.mac) == normMac) ||
                c.serial.equals(key, ignoreCase = true)
        }
    }
}
