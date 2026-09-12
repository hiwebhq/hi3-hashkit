package hi3.hashkit.domain.logs

/**
 * Classifies a single ESP-Miner / AxeOS log line into a severity and a category. Pure and
 * pattern-based (no cloud, no LLM), tuned to the ESP-IDF log format ("E (12345) tag: msg")
 * and common AxeOS messages. Unit-testable.
 */
object LogClassifier {

    enum class Severity { INFO, WARN, ERROR }

    enum class Category(val label: String) {
        POOL("Pool"),
        SHARE("Shares"),
        ASIC("ASIC"),
        THERMAL("Thermal"),
        NETWORK("Network"),
        SYSTEM("System"),
        OTHER("Other"),
    }

    data class Classified(val severity: Severity, val category: Category, val text: String)

    fun classify(line: String): Classified =
        Classified(severity(line), category(line), line)

    // Precompiled once — classify runs per stored line and per live-stream line.
    private val ERROR_WORDS = listOf("error", "fail", "fatal").map(::wordRegex)
    private val WARN_WORDS = listOf("warn", "retry").map(::wordRegex)

    private fun wordRegex(word: String) =
        Regex("\\b${Regex.escape(word)}", RegexOption.IGNORE_CASE)

    fun severity(line: String): Severity {
        val t = line.trimStart()
        // ESP-IDF prefixes: "E (12345) ...", "W (...) ...", and some builds embed " E (".
        val isErr = t.startsWith("E ") || t.startsWith("E(") || line.contains(" E (") ||
            ERROR_WORDS.any { it.containsMatchIn(line) }
        if (isErr) return Severity.ERROR
        val isWarn = t.startsWith("W ") || t.startsWith("W(") || line.contains(" W (") ||
            WARN_WORDS.any { it.containsMatchIn(line) }
        if (isWarn) return Severity.WARN
        return Severity.INFO
    }

    fun category(line: String): Category {
        val l = line.lowercase()
        return when {
            hasAny(l, "temp", "thermal", "overheat", "fan", "throttl") -> Category.THERMAL
            hasAny(l, "reject", "accepted", "share", "diff ", "difficulty", "bestdiff") -> Category.SHARE
            hasAny(l, "pool", "stratum", "mining.notify", "subscribe", "authorize", "job") -> Category.POOL
            hasAny(l, "asic", "bm13", "bm12", "chip", "nonce", "hashrate", "hash rate") -> Category.ASIC
            hasAny(l, "wifi", "wi-fi", "dhcp", "socket", "dns", "connect", "disconnect", "reconnect", "ip ") -> Category.NETWORK
            hasAny(l, "restart", "reboot", "boot", "watchdog", "brownout", "power", "reset", "heap", "self-test", "selftest") -> Category.SYSTEM
            else -> Category.OTHER
        }
    }

    private fun hasAny(haystack: String, vararg needles: String) = needles.any { it in haystack }
}
