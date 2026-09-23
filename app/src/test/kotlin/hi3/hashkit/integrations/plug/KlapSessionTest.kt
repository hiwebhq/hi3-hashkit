package hi3.hashkit.integrations.plug

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vectors computed independently (Python hashlib + openssl AES-128-CBC) for
 * local seed 00..0f, remote seed 10..1f, account user@example.com / secret.
 */
class KlapSessionTest {

    private val local = ByteArray(16) { it.toByte() }
    private val remote = ByteArray(16) { (it + 16).toByte() }
    private val creds = KasaCredentials("user@example.com", "secret")

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `auth hash is SHA256 of SHA1(user) and SHA1(password)`() {
        assertEquals("b039216532fc844e9ae0cc8fe3ea911c9ba09c641bb96a3e1f776d93f7b5ae9b", hex(KlapSession.authHash(creds)))
    }

    @Test
    fun `handshake hashes match the v2 seed ordering`() {
        val auth = KlapSession.authHash(creds)
        assertEquals("8ed3b905e654fbee23900d0244156a33029c025b71b03bfcc601eff33aaebb70", hex(KlapSession.handshake1Expected(local, remote, auth)))
        assertEquals("1e590e7a4dc128da48ed17e06fe23ddb6ec646bb6da2eb00708d590f194201d7", hex(KlapSession.handshake2Payload(local, remote, auth)))
    }

    @Test
    fun `first request uses seq plus one and a signed AES-CBC payload`() {
        val session = KlapSession(local, remote, KlapSession.authHash(creds))
        val (payload, seq) = session.encrypt("""{"method":"get_energy_usage"}""".toByteArray())
        assertEquals(-903964343, seq)
        assertEquals(
            "0717331528a00c041c25934b008d43b2b4e2c1c520484199f946da47a9ce4344" +
                "bc35a3fc4fa2b39a9abcfb2068047bbc8f4e36ceee5af3a71fecb3f93df4e4d5",
            hex(payload),
        )
        // Round trip: the device encrypts replies with the same key and IV‖seq.
        assertArrayEquals("""{"method":"get_energy_usage"}""".toByteArray(), session.decrypt(payload, seq))
        assertEquals(-903964342, session.encrypt(ByteArray(1)).second)
    }

    @Test
    fun `decrypt rejects payloads without a full signature`() {
        val session = KlapSession(local, remote, KlapSession.authHash(creds))
        runCatching { session.decrypt(unhex("00"), 1) }.let { assert(it.isFailure) }
    }
}
