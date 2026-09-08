package hi3.hashkit.adapters.cgminer

import hi3.hashkit.discovery.MinerHostValidator
import hi3.hashkit.domain.adapter.ActionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Control client for VNish (Antminer) firmware's local web API on port 80. Endpoints
 * verified against VNish 1.x (as consumed by pyasic's VNish web backend):
 *
 *   POST /api/v1/unlock            body {"pw":"<password>"}  -> {"token":"..."}
 *   POST /api/v1/system/reboot     Authorization: Bearer <token>
 *   POST /api/v1/mining/pause      Authorization: Bearer <token>
 *   POST /api/v1/mining/resume     Authorization: Bearer <token>
 *
 * The password never leaves the device except to the miner itself, over the LAN. Only
 * private/Tailscale hosts are allowed. Pool/preset changes (which require the full
 * /api/v1/settings object round-trip) are intentionally not implemented until verified.
 */
@Singleton
class VnishWebClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json".toMediaType()

    suspend fun reboot(host: String, password: String): ActionResult =
        withToken(host, password) { token -> post(host, "/api/v1/system/reboot", token) }

    suspend fun pauseResume(host: String, password: String, pause: Boolean): ActionResult =
        withToken(host, password) { token ->
            post(host, if (pause) "/api/v1/mining/pause" else "/api/v1/mining/resume", token)
        }

    private suspend fun withToken(host: String, password: String, block: (String) -> ActionResult): ActionResult =
        withContext(Dispatchers.IO) {
            if (!MinerHostValidator.resolvesToAllowed(host)) {
                return@withContext ActionResult.Failure("Refused: $host is not a private/Tailscale address")
            }
            if (password.isBlank()) {
                return@withContext ActionResult.Unsupported("Set the VNish web password in the miner's settings to use controls.")
            }
            val token = unlock(host, password)
                ?: return@withContext ActionResult.Failure("VNish login failed — check the web password.")
            block(token)
        }

    private fun unlock(host: String, password: String): String? = runCatching {
        val body = """{"pw":${jsonString(password)}}""".toRequestBody(jsonMedia)
        val req = Request.Builder().url("http://$host/api/v1/unlock").post(body).build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            json.parseToJsonElement(resp.body?.string().orEmpty())
                .jsonObject["token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun post(host: String, path: String, token: String): ActionResult = runCatching {
        val req = Request.Builder()
            .url("http://$host$path")
            .header("Authorization", "Bearer $token")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) ActionResult.Success
            else ActionResult.Failure("VNish answered HTTP ${resp.code}")
        }
    }.getOrElse { ActionResult.Failure(it.message ?: "Network error reaching the miner") }

    /** Minimal JSON string escaping for the password value. */
    private fun jsonString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
