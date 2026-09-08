package hi3.hashkit.adapters.cgminer

import hi3.hashkit.discovery.MinerHostValidator
import hi3.hashkit.domain.adapter.ActionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Control client for stock Bitmain (Antminer) firmware's authenticated web CGI. The
 * cgminer API on 4028 is read-only ("Restricted") by default, so control goes through the
 * lighttpd CGI under HTTP Digest auth (user `root`, realm "antMiner Configuration"):
 *
 *   GET /cgi-bin/reboot.cgi   (Digest auth) -> reboots the miner
 *
 * Verified from Bitmain's firmware CGI and common consumers (e.g. `curl --digest --user
 * root:<pw> http://<ip>/cgi-bin/reboot.cgi`). Pool/config changes go through
 * get/set_miner_conf.cgi (full-config round-trip) and are not implemented until verified.
 * LAN/Tailscale hosts only; the password never leaves the device except to the miner.
 */
@Singleton
class BitmainWebClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun reboot(host: String, user: String, password: String): ActionResult =
        digestGet(host, "/cgi-bin/reboot.cgi", user, password)

    private suspend fun digestGet(host: String, path: String, user: String, password: String): ActionResult =
        withContext(Dispatchers.IO) {
            if (!MinerHostValidator.resolvesToAllowed(host)) {
                return@withContext ActionResult.Failure("Refused: $host is not a private/Tailscale address")
            }
            if (password.isBlank()) {
                return@withContext ActionResult.Unsupported("Set the miner's root web password to use controls.")
            }
            val url = "http://$host$path"
            runCatching {
                // First request draws the 401 + WWW-Authenticate challenge.
                okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { challenge ->
                    if (challenge.isSuccessful) return@use ActionResult.Success // no auth required
                    if (challenge.code != 401) return@use ActionResult.Failure("Miner answered HTTP ${challenge.code}")
                    val header = challenge.header("WWW-Authenticate")
                        ?: return@use ActionResult.Failure("No auth challenge from miner")
                    val authz = digestHeader(header, "GET", path, user, password)
                        ?: return@use ActionResult.Failure("Unsupported auth challenge")
                    okHttpClient.newCall(
                        Request.Builder().url(url).get().header("Authorization", authz).build()
                    ).execute().use { resp ->
                        if (resp.isSuccessful) ActionResult.Success
                        else ActionResult.Failure("Miner rejected the command (HTTP ${resp.code}) — check the root password")
                    }
                }
            }.getOrElse { ActionResult.Failure(it.message ?: "Network error reaching the miner") }
        }

    /** Build an RFC 2617 Digest Authorization header (handles qop="auth" and no-qop). */
    private fun digestHeader(challenge: String, method: String, uri: String, user: String, pass: String): String? {
        if (!challenge.trimStart().startsWith("Digest", ignoreCase = true)) return null
        val p = parseChallenge(challenge)
        val realm = p["realm"] ?: return null
        val nonce = p["nonce"] ?: return null
        val qop = p["qop"]?.split(",")?.map { it.trim() }?.firstOrNull { it == "auth" }
        val opaque = p["opaque"]
        val ha1 = md5("$user:$realm:$pass")
        val ha2 = md5("$method:$uri")
        val response: String
        val cnonce = Random.nextBytes(8).joinToString("") { "%02x".format(it) }
        val nc = "00000001"
        response = if (qop != null) {
            md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }
        return buildString {
            append("Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$response\"")
            if (qop != null) append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
            opaque?.let { append(", opaque=\"$it\"") }
            p["algorithm"]?.let { append(", algorithm=$it") }
        }
    }

    private fun parseChallenge(header: String): Map<String, String> {
        val body = header.trim().removePrefix("Digest").removePrefix("digest").trim()
        return Regex("(\\w+)=(?:\"([^\"]*)\"|([^,]+))").findAll(body).associate { m ->
            m.groupValues[1] to (m.groupValues[2].ifEmpty { m.groupValues[3] }).trim()
        }
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
