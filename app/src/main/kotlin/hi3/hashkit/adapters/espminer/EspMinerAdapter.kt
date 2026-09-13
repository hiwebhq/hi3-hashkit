package hi3.hashkit.adapters.espminer

import hi3.hashkit.core.Totp
import hi3.hashkit.discovery.MinerHostValidator
import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.domain.adapter.FanControl
import hi3.hashkit.domain.adapter.MinerControlAdapter
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.domain.adapter.ProbeResult
import hi3.hashkit.domain.adapter.TelemetryResult
import hi3.hashkit.domain.adapter.TuneOptions
import hi3.hashkit.domain.model.Capability
import hi3.hashkit.domain.model.MinerCapabilities
import hi3.hashkit.domain.model.MinerIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter for Bitaxe and compatible ESP-Miner/AxeOS devices.
 *
 * Endpoints used, all verified against the tagged open-source firmware and/or real
 * devices (see EspMinerFirmware for version-specific behavior):
 *  - GET   /api/system/info     — identity + telemetry
 *  - GET   /api/system/asic     — firmware-approved tune options (newer firmware)
 *  - PATCH /api/system          — settings (pools, fan, frequency, coreVoltage)
 *  - POST  /api/system/restart  — reboot
 *
 * Controls are enabled only for official v2.x firmware; unknown forks are
 * monitoring-only with an explanatory capability reason. NerdQAxe devices additionally
 * get reboot, log streaming, and approved-option tuning — the subset verified against
 * the NerdQAxe firmware source (see EspMinerFirmware).
 */
@Singleton
class EspMinerAdapter @Inject constructor(
    private val client: OkHttpClient,
) : MinerControlAdapter {

    override val adapterType: String = TYPE
    override val displayName: String = "Bitaxe / ESP-Miner"
    override val defaultPort: Int = 80

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ------------------------------------------------------------------ read path ----

    override suspend fun probe(host: MinerHost): ProbeResult = withContext(Dispatchers.IO) {
        when (val fetched = get(host, "/api/system/info")) {
            is Fetched.Ok -> {
                val parsed = EspMinerParser.parseSystemInfo(fetched.body)
                if (parsed != null &&
                    (parsed.identity.asicModel != null || parsed.identity.firmwareVersion != null)
                ) {
                    ProbeResult.Supported(TYPE, parsed.identity, fetched.body)
                } else {
                    ProbeResult.NotThisDevice
                }
            }
            is Fetched.HttpError -> ProbeResult.NotThisDevice
            is Fetched.NetworkError -> ProbeResult.Unreachable(fetched.cause)
        }
    }

    override suspend fun getIdentity(host: MinerHost): MinerIdentity? =
        withContext(Dispatchers.IO) {
            (get(host, "/api/system/info") as? Fetched.Ok)
                ?.let { EspMinerParser.parseSystemInfo(it.body)?.identity }
        }

    override suspend fun getTelemetry(host: MinerHost): TelemetryResult =
        withContext(Dispatchers.IO) {
            when (val fetched = get(host, "/api/system/info")) {
                is Fetched.Ok -> {
                    val parsed = EspMinerParser.parseSystemInfo(fetched.body)
                    if (parsed != null) TelemetryResult.Success(parsed.telemetry, fetched.body)
                    else TelemetryResult.ParseError("Unparseable /api/system/info body", fetched.body)
                }
                is Fetched.HttpError -> TelemetryResult.Offline("HTTP ${fetched.code}")
                is Fetched.NetworkError -> TelemetryResult.Offline(fetched.cause)
            }
        }

    override fun getCapabilities(identity: MinerIdentity?): MinerCapabilities {
        val flavor = EspMinerFirmware.flavorOf(identity)
        if (!EspMinerFirmware.controlsSupported(flavor)) {
            val base = MinerCapabilities.monitoringOnly(EspMinerFirmware.UNVERIFIED_REASON)
            // Read-only log streaming over /api/ws is verified on NerdQAxe firmware.
            val verified = when (flavor) {
                // Reboot, log streaming, and approved-option tuning are verified on
                // NerdQAxe; pool/fan semantics differ and stay disabled.
                EspMinerFlavor.NERDQAXE ->
                    setOf(Capability.LOGS, Capability.REBOOT, Capability.APPLY_APPROVED_TUNE)
                // BC01 Lucky-Miner fork: /api/ws (101) and restart verified live
                // 2026-09-13; /api/system/asic publishes no tune options, so no tuning.
                EspMinerFlavor.LUCKY_MINER -> setOf(Capability.LOGS, Capability.REBOOT)
                else -> emptySet()
            }
            return if (verified.isEmpty()) base else base.copy(
                supported = base.supported + verified,
                unsupportedReasons = base.unsupportedReasons - verified,
            )
        }
        return MinerCapabilities(
            supported = setOf(
                Capability.TELEMETRY,
                Capability.LOGS,
                Capability.REBOOT,
                Capability.SET_POOLS,
                Capability.SET_FAN,
                Capability.APPLY_APPROVED_TUNE,
            ),
            unsupportedReasons = mapOf(
                Capability.SET_OPERATING_MODE to "ESP-Miner has no operating-mode concept; use tune profiles.",
                Capability.POWER_CONTROL to "ESP-Miner exposes no power on/off endpoint.",
            ),
        )
    }

    // ----------------------------------------------------------------- write path ----

    override suspend fun getTuneOptions(host: MinerHost): TuneOptions? =
        withContext(Dispatchers.IO) {
            val body = (get(host, "/api/system/asic") as? Fetched.Ok)?.body ?: return@withContext null
            val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return@withContext null
            val freqs = obj.intList("frequencyOptions") ?: return@withContext null
            val volts = obj.intList("voltageOptions") ?: return@withContext null
            if (freqs.isEmpty() || volts.isEmpty()) return@withContext null
            TuneOptions(
                frequencyOptionsMhz = freqs,
                voltageOptionsMv = volts,
                defaultFrequencyMhz = obj.intOrNull("defaultFrequency"),
                defaultVoltageMv = obj.intOrNull("defaultVoltage"),
            )
        }

    override suspend fun reboot(host: MinerHost): ActionResult =
        gated(host, EspMinerFirmware::rebootSupported) { _, _ ->
            when (val r = sendAuthed(host, "POST", "/api/system/restart", null)) {
                is Fetched.Ok -> ActionResult.Success
                is Fetched.HttpError -> ActionResult.Failure("Restart rejected: HTTP ${r.code}")
                is Fetched.NetworkError ->
                    // A restart drops the connection; treat an abrupt close as success.
                    if (r.cause.contains("unexpected end of stream", ignoreCase = true))
                        ActionResult.Success
                    else ActionResult.Failure("Restart failed: ${r.cause}")
            }
        }

    override suspend fun setPrimaryPool(host: MinerHost, url: String, port: Int, worker: String): ActionResult {
        if (url.isBlank() || worker.isBlank() || port !in 1..65535) {
            return ActionResult.Failure("Pool URL, port (1–65535) and worker are required.")
        }
        return gated(host) { flavor, info ->
            when (flavor) {
                EspMinerFlavor.OFFICIAL_V2_FLAT_POOLS -> {
                    val payload = buildJsonObject {
                        put("stratumURL", url)
                        put("stratumPort", port)
                        put("stratumUser", worker)
                    }
                    patch(host, payload).toActionResult()
                }
                EspMinerFlavor.OFFICIAL_V2_POOLS_ARRAY -> {
                    // Echo the whole pools array verbatim (masked passwords "*****" are
                    // preserved by the firmware; omitting them would wipe them), editing
                    // only the primary pool's URL/port/user.
                    val pools = info["pools"] as? JsonArray
                        ?: return@gated ActionResult.Failure("Firmware did not report a pools array.")
                    val primaryIndex = (info["primaryPoolIndex"] as? JsonPrimitive)?.intOrNull ?: 0
                    val edited = buildJsonArray {
                        pools.forEachIndexed { i, pool ->
                            val poolObj = pool.jsonObject
                            if (poolObj.intOrNull("id") == primaryIndex || (poolObj["id"] == null && i == primaryIndex)) {
                                add(JsonObject(poolObj.toMutableMap().apply {
                                    put("stratumURL", JsonPrimitive(url))
                                    put("stratumPort", JsonPrimitive(port))
                                    put("stratumUser", JsonPrimitive(worker))
                                }))
                            } else {
                                add(pool)
                            }
                        }
                    }
                    patch(host, buildJsonObject { put("pools", edited) }).toActionResult()
                }
                else -> ActionResult.Unsupported(EspMinerFirmware.UNVERIFIED_REASON)
            }
        }
    }

    override suspend fun setFan(host: MinerHost, config: FanControl): ActionResult =
        gated(host) { _, _ ->
            val payload = when (config) {
                is FanControl.Automatic -> buildJsonObject {
                    put("autofanspeed", true)
                    config.targetTempC?.let {
                        if (it !in 35..66) return@gated ActionResult.Failure("Target temp must be 35–66°C.")
                        put("temptarget", it)
                    }
                }
                is FanControl.Manual -> {
                    if (config.percent !in 0..100) return@gated ActionResult.Failure("Fan percent must be 0–100.")
                    buildJsonObject {
                        put("autofanspeed", false)
                        put("manualFanSpeed", config.percent)
                    }
                }
            }
            patch(host, payload).toActionResult()
        }

    override suspend fun applyTune(host: MinerHost, frequencyMhz: Int, coreVoltageMv: Int): ActionResult =
        gated(host, EspMinerFirmware::tuneSupported) { _, _ ->
            val options = getTuneOptions(host)
                ?: return@gated ActionResult.Unsupported(
                    "This firmware does not publish approved tune options (/api/system/asic); tuning is disabled."
                )
            if (frequencyMhz !in options.frequencyOptionsMhz) {
                return@gated ActionResult.Failure(
                    "$frequencyMhz MHz is not in the firmware-approved list ${options.frequencyOptionsMhz}."
                )
            }
            if (coreVoltageMv !in options.voltageOptionsMv) {
                return@gated ActionResult.Failure(
                    "$coreVoltageMv mV is not in the firmware-approved list ${options.voltageOptionsMv}."
                )
            }
            patch(
                host,
                buildJsonObject {
                    put("frequency", frequencyMhz)
                    put("coreVoltage", coreVoltageMv)
                },
            ).toActionResult()
        }

    // ---------------------------------------------------------------- http helpers ----

    /** Run a control action only after re-verifying the firmware flavor supports controls. */
    private suspend fun gated(
        host: MinerHost,
        allow: (EspMinerFlavor) -> Boolean = EspMinerFirmware::controlsSupported,
        block: suspend (EspMinerFlavor, JsonObject) -> ActionResult,
    ): ActionResult = withContext(Dispatchers.IO) {
        val body = (get(host, "/api/system/info") as? Fetched.Ok)?.body
            ?: return@withContext ActionResult.Failure("Miner is unreachable; no changes were made.")
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return@withContext ActionResult.Failure("Miner response unparseable; no changes were made.")
        val identity = EspMinerParser.parseSystemInfo(body)?.identity
        val flavor = EspMinerFirmware.flavorOf(identity)
        if (!allow(flavor)) {
            return@withContext ActionResult.Unsupported(EspMinerFirmware.UNVERIFIED_REASON)
        }
        block(flavor, obj)
    }

    private fun get(host: MinerHost, path: String): Fetched = send(host, "GET", path, null)

    private fun patch(host: MinerHost, payload: JsonObject): Fetched =
        sendAuthed(host, "PATCH", "/api/system", payload.toString())

    // ------------------------------------------------------------ NerdQAxe OTP auth ----

    /** Minted `X-OTP-Session` tokens per host, valid ~24h (see NerdQAxe otp/doc.md). */
    private val otpSessions = java.util.concurrent.ConcurrentHashMap<String, OtpSession>()

    private data class OtpSession(val token: String, val expiresAtMs: Long)

    /**
     * Write path with NerdQAxe OTP handling. NerdQAxe firmware can protect all settings
     * writes with TOTP (RFC 6238, SHA-1/6-digit/30 s — verified in the v1.1.0 source,
     * otp.cpp): with OTP enabled every write needs an `X-TOTP` code or an `X-OTP-Session`
     * token from `POST /api/v2/otp/session`. The miner's stored admin credential is the
     * base32 TOTP secret; on a 401 we mint a session token from it and retry once.
     * Official firmware and OTP-disabled NerdQAxe never return 401, so this is a no-op
     * for them.
     */
    private fun sendAuthed(host: MinerHost, method: String, path: String, body: String?): Fetched {
        val key = "${host.host}:${host.port}"
        val cached = otpSessions[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }
        val headers = cached?.let { mapOf(HEADER_OTP_SESSION to it.token) }.orEmpty()
        val first = send(host, method, path, body, headers)
        if (first !is Fetched.HttpError || first.code != HTTP_UNAUTHORIZED) return first
        val secret = host.secret?.takeIf { it.isNotBlank() } ?: return first
        val token = mintOtpSession(host, secret) ?: return first
        return send(host, method, path, body, mapOf(HEADER_OTP_SESSION to token))
    }

    /**
     * Trade a fresh TOTP code for a session token; caches it per host. Null on failure.
     * Both path layouts exist in the wild — current builds serve /api/otp/session
     * (verified on a live NerdQAxe++ reporting v1.1.0), while the tagged v1.1.0 source
     * registers /api/v2/otp/session — so try them in that order.
     */
    private fun mintOtpSession(host: MinerHost, secretBase32: String): String? {
        val code = Totp.code(secretBase32) ?: return null
        return OTP_SESSION_PATHS.firstNotNullOfOrNull { path -> requestSessionToken(host, path, code) }
    }

    private fun requestSessionToken(host: MinerHost, path: String, totpCode: String): String? {
        val response = send(host, "POST", path, null, mapOf("X-TOTP" to totpCode))
        val body = (response as? Fetched.Ok)?.body ?: return null
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val token = (obj["token"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
        val ttlMs = (obj["ttlMs"] as? JsonPrimitive)?.longOrNull ?: DEFAULT_OTP_TTL_MS
        // Renew a minute early so a token never expires mid-request.
        otpSessions["${host.host}:${host.port}"] =
            OtpSession(token, System.currentTimeMillis() + ttlMs - SESSION_RENEW_MARGIN_MS)
        return token
    }

    private fun send(
        host: MinerHost,
        method: String,
        path: String,
        body: String?,
        headers: Map<String, String> = emptyMap(),
    ): Fetched {
        if (!MinerHostValidator.resolvesToAllowed(host.host)) {
            return Fetched.NetworkError("Refused: ${host.host} is not a private/Tailscale address")
        }
        val builder = Request.Builder().url("http://${host.host}:${host.port}$path")
        headers.forEach { (name, value) -> builder.header(name, value) }
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((body ?: "").toRequestBody(JSON_TYPE))
            "PATCH" -> builder.patch((body ?: "{}").toRequestBody(JSON_TYPE))
        }
        return try {
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return Fetched.HttpError(resp.code)
                Fetched.Ok(resp.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            Fetched.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun Fetched.toActionResult(): ActionResult = when (this) {
        is Fetched.Ok -> ActionResult.Success
        is Fetched.HttpError ->
            if (code == HTTP_UNAUTHORIZED) ActionResult.Failure(
                "Miner rejected the change: HTTP 401 (authentication). If this device has " +
                    "OTP enabled, save its TOTP secret (from the enrollment QR) as the " +
                    "miner's admin credential."
            )
            else ActionResult.Failure("Miner rejected the change: HTTP $code")
        is Fetched.NetworkError -> ActionResult.Failure("Network error: $cause")
    }

    private fun JsonObject.intList(key: String): List<Int>? =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private sealed interface Fetched {
        data class Ok(val body: String) : Fetched
        data class HttpError(val code: Int) : Fetched
        data class NetworkError(val cause: String) : Fetched
    }

    companion object {
        const val TYPE = "espminer"
        private val JSON_TYPE = "application/json".toMediaType()
        private const val HEADER_OTP_SESSION = "X-OTP-Session"
        private const val HTTP_UNAUTHORIZED = 401
        private const val DEFAULT_OTP_TTL_MS = 24 * 3600 * 1000L
        private const val SESSION_RENEW_MARGIN_MS = 60_000L
        private val OTP_SESSION_PATHS = listOf("/api/otp/session", "/api/v2/otp/session")
    }
}
