package hi3.hashkit.domain.tag

/**
 * A miner marker read from a QR code or an NFC tag. The canonical payload is a `key=value`
 * text block (one field per line), shared by both:
 *
 *   name=Bitaxe-01
 *   mac=AA:BB:CC:DD:EE:FF
 *   ip=10.0.0.42
 *   location=Rack 1 / Shelf 2
 *
 * A bare string with no `=` (a legacy QR holding just an id / name / IP / MAC) is kept as
 * [rawValue] and matched leniently. A `hi3miner://…?mac=…&ip=…` URI is also accepted.
 */
data class MinerTag(
    val name: String? = null,
    val mac: String? = null,
    val ip: String? = null,
    val location: String? = null,
    /** Set when the payload was a single bare value rather than key=value/URI fields. */
    val rawValue: String? = null,
) {
    val hasFields: Boolean get() = name != null || mac != null || ip != null || location != null

    companion object {
        fun parse(raw: String?): MinerTag? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null

            // hi3miner://miner?name=..&mac=..&ip=..&loc=..
            if (text.startsWith("hi3miner:", ignoreCase = true)) {
                val query = text.substringAfter('?', "")
                if (query.isNotEmpty()) {
                    val map = query.split('&').mapNotNull { kv ->
                        val i = kv.indexOf('='); if (i <= 0) return@mapNotNull null
                        kv.substring(0, i).trim().lowercase() to urlDecode(kv.substring(i + 1))
                    }.toMap()
                    fromMap(map)?.let { return it }
                }
            }

            // key=value block (newline / ';' separated).
            if (text.contains('=')) {
                val map = text.split('\n', '\r', ';').mapNotNull { line ->
                    val i = line.indexOf('='); if (i <= 0) return@mapNotNull null
                    line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
                }.toMap()
                fromMap(map)?.let { return it }
            }

            // Bare legacy value.
            return MinerTag(rawValue = text)
        }

        private fun fromMap(map: Map<String, String>): MinerTag? {
            val tag = MinerTag(
                name = map["name"]?.takeIf { it.isNotEmpty() },
                mac = map["mac"]?.takeIf { it.isNotEmpty() },
                ip = (map["ip"] ?: map["host"])?.takeIf { it.isNotEmpty() },
                location = (map["location"] ?: map["loc"])?.takeIf { it.isNotEmpty() },
            )
            return if (tag.hasFields) tag else null
        }

        /** MAC without separators, lowercased — for tolerant comparison. */
        fun normalizeMac(mac: String?): String? =
            mac?.replace(":", "")?.replace("-", "")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        private fun urlDecode(s: String): String =
            runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s).trim()
    }
}
