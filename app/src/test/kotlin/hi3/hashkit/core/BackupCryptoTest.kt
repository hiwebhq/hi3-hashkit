package hi3.hashkit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    private val payload = """{"format":1,"miners":[{"name":"0x203","host":"10.0.0.203"}]}"""

    @Test
    fun `round-trips with the correct passphrase`() {
        val enc = BackupCrypto.encrypt(payload, "correct horse battery staple")
        assertTrue(BackupCrypto.isEncrypted(enc))
        assertEquals(payload, BackupCrypto.decrypt(enc, "correct horse battery staple"))
    }

    @Test
    fun `wrong passphrase fails to decrypt`() {
        val enc = BackupCrypto.encrypt(payload, "right")
        assertNull(BackupCrypto.decrypt(enc, "wrong"))
    }

    @Test
    fun `ciphertext does not contain plaintext and salt-iv randomize output`() {
        val a = BackupCrypto.encrypt(payload, "pw")
        val b = BackupCrypto.encrypt(payload, "pw")
        assertFalse(a.contains("10.0.0.203"))
        assertFalse(a.contains("0x203"))
        // Random salt+iv per call -> different envelopes for identical input/passphrase.
        assertNotEquals(a, b)
        assertEquals(payload, BackupCrypto.decrypt(b, "pw"))
    }

    @Test
    fun `plaintext json is not detected as encrypted`() {
        assertFalse(BackupCrypto.isEncrypted(payload))
        assertNull(BackupCrypto.decrypt(payload, "pw"))
    }

    @Test
    fun `corrupt envelope returns null instead of throwing`() {
        assertNull(BackupCrypto.decrypt("HI3ENC1:210000:garbage", "pw"))
        assertNull(BackupCrypto.decrypt("HI3ENC1:not:a:valid:envelope", "pw"))
    }
}
