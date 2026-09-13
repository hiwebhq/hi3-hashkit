package hi3.hashkit.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 TOTP with Google-Authenticator defaults (HMAC-SHA1, 6 digits, 30 s period) —
 * the parameters NerdQAxe firmware uses for its OTP-protected control API. The secret is
 * the base32 string from the device's enrollment QR (otpauth://totp/...?secret=...).
 */
object Totp {

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Decode RFC 4648 base32, skipping padding and any non-alphabet characters (spaces,
     * dashes) the way the firmware's decoder does, so a secret pasted with formatting
     * still works. Returns null if nothing decodable remains.
     */
    @Suppress("MagicNumber") // base32 bit layout per RFC 4648
    fun base32Decode(s: String): ByteArray? {
        var buffer = 0
        var bitsLeft = 0
        val out = ArrayList<Byte>()
        for (ch in s.uppercase()) {
            val v = BASE32_ALPHABET.indexOf(ch)
            if (v < 0) continue // skip '=', spaces, and other separators
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return if (out.isEmpty()) null else out.toByteArray()
    }

    /** Current 6-digit code for [secretBase32], or null if the secret is not base32. */
    @Suppress("MagicNumber") // HOTP counter/truncation bit layout per RFC 4226
    fun code(
        secretBase32: String,
        epochSeconds: Long = System.currentTimeMillis() / 1000,
        periodSeconds: Int = 30,
        digits: Int = 6,
    ): String? {
        val key = base32Decode(secretBase32) ?: return null
        val counter = epochSeconds / periodSeconds
        val msg = ByteArray(8) { i -> ((counter shr ((7 - i) * 8)) and 0xFF).toByte() }
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "RAW")) }
        val hash = mac.doFinal(msg)
        // RFC 4226 dynamic truncation.
        val offset = hash.last().toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        var mod = 1
        repeat(digits) { mod *= DECIMAL_BASE }
        return (binary % mod).toString().padStart(digits, '0')
    }

    private const val DECIMAL_BASE = 10
}
