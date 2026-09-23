package hi3.hashkit.integrations.plug

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** TP-Link cloud account used by newer Kasa/Tapo plugs for their local KLAP login. */
data class KasaCredentials(val username: String, val password: String)

/**
 * Pure crypto for TP-Link's KLAP v2 local protocol (Kasa KP125M/EP25/HS100 v4+, Tapo),
 * as implemented by the open-source python-kasa project:
 *
 *  - `auth_hash = SHA256(SHA1(username) ‖ SHA1(password))` (v2; v1 used MD5 chains)
 *  - handshake1: client sends 16 random bytes (`local_seed`); the device answers
 *    `remote_seed ‖ SHA256(local_seed ‖ remote_seed ‖ auth_hash)`, which proves the account
 *  - handshake2: client answers `SHA256(remote_seed ‖ local_seed ‖ auth_hash)`
 *  - session: `key = SHA256("lsk" ‖ seeds ‖ auth)[0..16)`, `iv = SHA256("iv" ‖ …)[0..12)` with
 *    the last 4 bytes as the signed starting sequence, `sig = SHA256("ldk" ‖ …)[0..28)`
 *  - each request: `seq += 1`, AES-128-CBC/PKCS7 with IV `iv ‖ seq`, payload =
 *    `SHA256(sig ‖ seq ‖ ciphertext) ‖ ciphertext`, posted to `/app/request?seq=<seq>`
 *
 * This class is network-free so the derivation can be unit-tested against known vectors.
 */
class KlapSession(localSeed: ByteArray, remoteSeed: ByteArray, authHash: ByteArray) {

    private val key: ByteArray = sha256(LSK + localSeed + remoteSeed + authHash).copyOf(KEY_BYTES)
    private val sig: ByteArray = sha256(LDK + localSeed + remoteSeed + authHash).copyOf(SIG_BYTES)
    private val iv: ByteArray
    private var seq: Int

    init {
        val ivFull = sha256(IV + localSeed + remoteSeed + authHash)
        iv = ivFull.copyOf(IV_BYTES)
        seq = ByteBuffer.wrap(ivFull, ivFull.size - SEQ_BYTES, SEQ_BYTES).int
    }

    /** Encrypt one request; returns the wire payload and the sequence number to send with it. */
    @Synchronized
    fun encrypt(plaintext: ByteArray): Pair<ByteArray, Int> {
        seq += 1
        val seqBytes = seqBytes(seq)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv + seqBytes))
        val ciphertext = cipher.doFinal(plaintext)
        val signature = sha256(sig + seqBytes + ciphertext)
        return (signature + ciphertext) to seq
    }

    /** Decrypt a response to the request that was sent with [seq]. */
    fun decrypt(payload: ByteArray, seq: Int): ByteArray {
        require(payload.size > SIGNATURE_BYTES) { "KLAP response too short" }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv + seqBytes(seq)))
        return cipher.doFinal(payload, SIGNATURE_BYTES, payload.size - SIGNATURE_BYTES)
    }

    companion object {
        private val LSK = "lsk".toByteArray()
        private val LDK = "ldk".toByteArray()
        private val IV = "iv".toByteArray()
        private const val TRANSFORM = "AES/CBC/PKCS5Padding" // PKCS5 == PKCS7 for 16-byte AES blocks in JCE
        private const val KEY_BYTES = 16
        private const val IV_BYTES = 12
        private const val SEQ_BYTES = 4
        private const val SIG_BYTES = 28
        private const val SIGNATURE_BYTES = 32
        const val SEED_BYTES = 16

        fun authHash(credentials: KasaCredentials): ByteArray =
            sha256(sha1(credentials.username.toByteArray()) + sha1(credentials.password.toByteArray()))

        /** What the device must return after `remote_seed` in handshake1 for [authHash] to be right. */
        fun handshake1Expected(localSeed: ByteArray, remoteSeed: ByteArray, authHash: ByteArray): ByteArray =
            sha256(localSeed + remoteSeed + authHash)

        fun handshake2Payload(localSeed: ByteArray, remoteSeed: ByteArray, authHash: ByteArray): ByteArray =
            sha256(remoteSeed + localSeed + authHash)

        fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
        fun sha1(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(data)

        private fun seqBytes(seq: Int): ByteArray = ByteBuffer.allocate(SEQ_BYTES).putInt(seq).array()
    }
}
