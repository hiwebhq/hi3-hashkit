package hi3.hashkit.core

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based encryption for backup files: PBKDF2-HMAC-SHA256 (210k iterations)
 * derives an AES-256 key from the user's passphrase and a random salt; the payload is
 * sealed with AES-256/GCM. Unlike [KeystoreCrypto] (device-bound), an encrypted backup
 * is portable — it restores on any device given the passphrase.
 *
 * Envelope: a single line `HI3ENC1:<iter>:<saltB64>:<ivB64>:<ctB64>` — trivially
 * detectable and dependency-free (uses only JVM crypto + Base64, so it unit-tests on
 * the JVM and runs on Android 26+).
 */
object BackupCrypto {

    private const val PREFIX = "HI3ENC1:"
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256

    fun isEncrypted(content: String): Boolean = content.trimStart().startsWith(PREFIX)

    fun encrypt(plaintext: String, passphrase: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val e = Base64.getEncoder()
        return "$PREFIX$ITERATIONS:${e.encodeToString(salt)}:${e.encodeToString(iv)}:${e.encodeToString(ct)}"
    }

    /** Returns the plaintext, or null if the passphrase is wrong or the file is corrupt. */
    fun decrypt(envelope: String, passphrase: String): String? = runCatching {
        val parts = envelope.trim().removePrefix(PREFIX).split(":")
        require(parts.size == 4)
        val d = Base64.getDecoder()
        val iter = parts[0].toInt()
        val salt = d.decode(parts[1]); val iv = d.decode(parts[2]); val ct = d.decode(parts[3])
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iter), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS))
        return SecretKeySpec(key.encoded, "AES")
    }
}
