package hi3.hashkit.core

/**
 * Offline validator for advanced-feature unlock codes. This is a **placeholder scheme** for
 * a future licensing model — it proves the gate end-to-end without a server. Replace the
 * body with real verification (server check or an Ed25519-signed license) before shipping
 * paid unlocks; the call sites and settings plumbing stay the same.
 *
 * Current scheme: a code like `HI3-XXXX-XXXX` (letters/digits) is valid when its trailing
 * two characters are a checksum of the preceding characters. Good enough to demo lock/unlock;
 * NOT cryptographically secure.
 */
object LicenseValidator {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun isValid(code: String?): Boolean {
        val cleaned = code?.uppercase()?.replace("-", "")?.replace(" ", "") ?: return false
        if (!cleaned.startsWith("HI3") || cleaned.length < 8) return false
        if (cleaned.any { it !in ALPHABET }) return false
        val payload = cleaned.dropLast(2)
        val check = cleaned.takeLast(2)
        return checksum(payload) == check
    }

    /** Format a raw payload into a valid code (for tests / issuing codes). */
    fun issue(payload: String): String {
        val p = ("HI3" + payload.uppercase().filter { it in ALPHABET }).take(14)
        return p + checksum(p)
    }

    private fun checksum(payload: String): String {
        var sum = 0
        for ((i, c) in payload.withIndex()) sum += (ALPHABET.indexOf(c) + 1) * (i + 1)
        val a = ALPHABET[sum % ALPHABET.length]
        val b = ALPHABET[(sum / ALPHABET.length) % ALPHABET.length]
        return "$a$b"
    }
}
