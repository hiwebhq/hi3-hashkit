package hi3.hashkit.integrations.plug

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP transport for KLAP-protocol Kasa/Tapo plugs (see [KlapSession] for the crypto):
 * `POST /app/handshake1`, `POST /app/handshake2`, then `POST /app/request?seq=N`, all on
 * port 80 with the `TP_SESSIONID` cookie the device sets during handshake1.
 *
 * Sessions are cached per host and re-established once when the device rejects a request
 * (session timeout, reboot). Only private/Tailscale hosts are contacted by the caller.
 */
@Singleton
class KlapClient @Inject constructor(
    private val http: OkHttpClient,
) {
    sealed interface Result {
        /** `result` object of a successful reply (`error_code` 0). */
        data class Ok(val result: JsonObject, val raw: String) : Result
        /** handshake1 hash mismatch: the TP-Link account does not own this plug. */
        data object AuthFailed : Result
        data class Error(val cause: String) : Result
    }

    private class Live(val session: KlapSession, val cookie: String)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val sessions = ConcurrentHashMap<String, Live>()
    private val random = SecureRandom()

    /** Send one JSON-RPC style request, e.g. `{"method":"get_energy_usage"}`. */
    fun request(host: String, credentials: KasaCredentials, body: String): Result {
        val first = sessions[host]?.let { exchange(host, it, body) }
        if (first is Result.Ok) return first
        // No session, or the cached one was rejected: handshake again and retry once.
        sessions.remove(host)
        val live = when (val hs = handshake(host, credentials)) {
            is Handshake.Ok -> hs.live
            Handshake.AuthFailed -> return Result.AuthFailed
            is Handshake.Error -> return Result.Error(hs.cause)
        }
        sessions[host] = live
        return exchange(host, live, body)
    }

    /** Drop the cached session for a host (e.g. after the account credentials change). */
    fun forgetAll() = sessions.clear()

    private sealed interface Handshake {
        data class Ok(val live: Live) : Handshake
        data object AuthFailed : Handshake
        data class Error(val cause: String) : Handshake
    }

    private fun handshake(host: String, credentials: KasaCredentials): Handshake {
        val localSeed = ByteArray(KlapSession.SEED_BYTES).also { random.nextBytes(it) }
        val authHash = KlapSession.authHash(credentials)
        val (body, cookie) = post(host, "/app/handshake1", localSeed, cookie = null)
            .getOrElse { return Handshake.Error("handshake1: ${it.message}") }
        if (body.size < KlapSession.SEED_BYTES + HASH_BYTES) {
            return Handshake.Error("handshake1 body too short (${body.size})")
        }
        val sessionCookie = cookie ?: return Handshake.Error("handshake1 set no $COOKIE_NAME cookie")
        return finishHandshake(host, localSeed, authHash, body, sessionCookie)
    }

    /** Verify the device's proof of the account, then answer with ours (handshake2). */
    private fun finishHandshake(
        host: String,
        localSeed: ByteArray,
        authHash: ByteArray,
        handshake1Body: ByteArray,
        cookie: String,
    ): Handshake {
        val remoteSeed = handshake1Body.copyOfRange(0, KlapSession.SEED_BYTES)
        val serverHash = handshake1Body.copyOfRange(KlapSession.SEED_BYTES, KlapSession.SEED_BYTES + HASH_BYTES)
        if (!serverHash.contentEquals(KlapSession.handshake1Expected(localSeed, remoteSeed, authHash))) {
            return Handshake.AuthFailed
        }
        val proof = KlapSession.handshake2Payload(localSeed, remoteSeed, authHash)
        post(host, "/app/handshake2", proof, cookie)
            .getOrElse { return Handshake.Error("handshake2: ${it.message}") }
        return Handshake.Ok(Live(KlapSession(localSeed, remoteSeed, authHash), cookie))
    }

    private fun exchange(host: String, live: Live, body: String): Result {
        val (payload, seq) = live.session.encrypt(body.toByteArray())
        val (raw, _) = post(host, "/app/request?seq=$seq", payload, live.cookie)
            .getOrElse { return Result.Error("request: ${it.message}") }
        val text = runCatching { String(live.session.decrypt(raw, seq)) }
            .getOrElse { return Result.Error("decrypt failed: ${it.message}") }
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrElse { return Result.Error("unparseable reply") }
        val code = (obj["error_code"] as? JsonPrimitive)?.intOrNull ?: -1
        return if (code != 0) Result.Error("error_code $code")
        else Result.Ok(obj["result"] as? JsonObject ?: JsonObject(emptyMap()), text)
    }

    /** POST raw bytes; success = (body bytes, TP_SESSIONID cookie if the device set one). */
    private fun post(
        host: String,
        path: String,
        body: ByteArray,
        cookie: String?,
    ): kotlin.Result<Pair<ByteArray, String?>> =
        runCatching {
            val builder = Request.Builder().url("http://$host$path").post(body.toRequestBody(OCTET))
            if (cookie != null) builder.header("Cookie", cookie)
            http.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val setCookie = resp.headers("Set-Cookie")
                    .firstOrNull { it.startsWith(COOKIE_NAME) }
                    ?.substringBefore(';')
                (resp.body?.bytes() ?: ByteArray(0)) to setCookie
            }
        }

    companion object {
        private val OCTET = "application/octet-stream".toMediaType()
        private const val COOKIE_NAME = "TP_SESSIONID"
        private const val HASH_BYTES = 32

        /** Request bodies; `requestTimeMils` mirrors what the official app sends. */
        fun method(name: String, params: String? = null): String =
            "{\"method\":\"$name\"" + (params?.let { ",\"params\":$it" } ?: "") +
                ",\"requestTimeMils\":${System.currentTimeMillis()}}"
    }
}
