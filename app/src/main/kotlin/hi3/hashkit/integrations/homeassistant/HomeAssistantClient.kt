package hi3.hashkit.integrations.homeassistant

import hi3.hashkit.discovery.MinerHostValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a single numeric sensor from a local Home Assistant instance over its REST API:
 *   GET {baseUrl}/api/states/{entityId}  with  Authorization: Bearer <long-lived token>
 *   → {"entity_id":"sensor.solar_export","state":"1234.5","attributes":{...}}
 *
 * Used for solar-surplus mining: the sensor is the user's grid-export/surplus power in watts.
 * Only private/Tailscale HA hosts are contacted — the token never leaves the local network.
 * The token is sent only in the Authorization header to the user's own HA host, never logged.
 */
@Singleton
class HomeAssistantClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    sealed interface Result {
        data class Ok(val watts: Double, val rawState: String, val unit: String?) : Result
        data class Error(val message: String) : Result
    }

    /**
     * @param baseUrl e.g. http://homeassistant.local:8123 (private/Tailscale only)
     * @param token a Home Assistant long-lived access token
     * @param entityId e.g. sensor.solar_surplus_power (state read as watts)
     */
    suspend fun readWatts(baseUrl: String, token: String, entityId: String): Result =
        withContext(Dispatchers.IO) {
            val base = baseUrl.trim().trimEnd('/')
            if (base.isEmpty() || token.isBlank() || entityId.isBlank()) {
                return@withContext Result.Error("Configure HA URL, token and sensor first.")
            }
            val host = runCatching { java.net.URI(base).host }.getOrNull()
            if (host == null || !MinerHostValidator.resolvesToAllowed(host)) {
                return@withContext Result.Error("Home Assistant must be a private/Tailscale address.")
            }
            val url = "$base/api/states/${entityId.trim()}"
            runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${token.trim()}")
                    .header("Content-Type", "application/json")
                    .get()
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@use Result.Error("HA returned HTTP ${resp.code}.")
                    }
                    val body = resp.body?.string().orEmpty()
                    val state = Regex("\"state\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
                    val unit = Regex("\"unit_of_measurement\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
                    val raw = state?.trim().orEmpty()
                    val watts = raw.toDoubleOrNull()
                        ?: return@use Result.Error("Sensor state \"$raw\" isn't a number.")
                    // Normalise kW → W if the sensor reports kilowatts.
                    val normalised = if (unit?.equals("kW", ignoreCase = true) == true) watts * 1000.0 else watts
                    Result.Ok(normalised, raw, unit)
                }
            }.getOrElse { Result.Error("Couldn't reach Home Assistant: ${it.message}") }
        }
}
