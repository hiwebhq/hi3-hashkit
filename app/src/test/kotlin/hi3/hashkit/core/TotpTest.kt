package hi3.hashkit.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TotpTest {

    /** RFC 6238 Appendix B SHA-1 secret "12345678901234567890" in base32. */
    private val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun `matches RFC 6238 SHA-1 test vectors`() {
        // The RFC lists 8-digit codes; the 6-digit code is the same value mod 10^6.
        assertEquals("287082", Totp.code(rfcSecret, epochSeconds = 59))
        assertEquals("081804", Totp.code(rfcSecret, epochSeconds = 1_111_111_109))
        assertEquals("050471", Totp.code(rfcSecret, epochSeconds = 1_111_111_111))
        assertEquals("005924", Totp.code(rfcSecret, epochSeconds = 1_234_567_890))
        assertEquals("279037", Totp.code(rfcSecret, epochSeconds = 2_000_000_000))
    }

    @Test
    fun `base32 decode skips separators and padding like the firmware decoder`() {
        val clean = Totp.base32Decode(rfcSecret)!!
        assertArrayEquals("12345678901234567890".toByteArray(), clean)
        // Lowercase, spaces, dashes, and '=' padding must not change the result.
        assertArrayEquals(clean, Totp.base32Decode("gezd gnbv-gy3t qojq GEZD GNBV GY3T QOJQ=="))
    }

    @Test
    fun `unusable secret yields null instead of a bogus code`() {
        assertNull(Totp.code("!!!", epochSeconds = 59))
        assertNull(Totp.base32Decode("10 89"))  // '1' and '0' are not base32
    }
}
