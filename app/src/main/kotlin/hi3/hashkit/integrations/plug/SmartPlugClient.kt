package hi3.hashkit.integrations.plug

import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.discovery.MinerHostValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Local-network smart plugs used for the over-temp safety cutoff and wall-power metering. */
enum class PlugType(val label: String, @androidx.annotation.StringRes val labelRes: Int) {
    TASMOTA("Tasmota", hi3.hashkit.R.string.enum_plug_tasmota),
    SHELLY("Shelly", hi3.hashkit.R.string.enum_plug_shelly),
    KASA("Kasa / TP-Link", hi3.hashkit.R.string.enum_plug_kasa),
    WEBHOOK("Generic webhook", hi3.hashkit.R.string.enum_plug_webhook);

    companion object {
        fun fromName(name: String?): PlugType? = entries.firstOrNull { it.name == name }
    }
}

/**
 * One metering read from a plug. Energy counters are whatever the plug keeps:
 * [energyTodayWh] resets at the plug's local midnight, [energyTotalWh] is its lifetime
 * (or since-reset) counter. Either may be null when the firmware doesn't report it.
 */
data class PlugReading(
    val powerW: Double?,
    val energyTodayWh: Double? = null,
    val energyTotalWh: Double? = null,
)

/**
 * Switches and meters local smart plugs over their LAN APIs — no cloud. Verified command
 * shapes:
 *  - Tasmota: GET http://<host>/cm?cmnd=Power%20Off | Power%20On; `Status 8` for the meter
 *  - Shelly:  GET /rpc/Switch.Set?id=0&on=false (Gen2), falling back to /relay/0?turn=off (Gen1)
 *  - Kasa (legacy, e.g. HS110/KP115): TCP :9999 autokey-XOR JSON — `set_relay_state`,
 *    `emeter get_realtime` (power_mw, total_wh); verified live on a KP115
 *  - Kasa (KLAP, e.g. KP125M/EP25 and Tapo): HTTP :80 KLAP v2 session with the user's
 *    TP-Link account (see [KlapClient]) — `set_device_info`, `get_energy_usage`
 *  - Webhook: GET the user-supplied on/off URL
 *
 * Only private LAN / Tailscale hosts are allowed. The safety monitor calls [turnOff] only;
 * turning a miner back on is always a manual action.
 */
@Singleton
class SmartPlugClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val klap: KlapClient,
    private val settings: SettingsRepository,
) {
    data class Plug(
        val type: PlugType,
        val host: String?,
        val onUrl: String?,
        val offUrl: String?,
    )

    /** Hosts known to speak KLAP (newer Kasa); skips the legacy port's connect attempt. */
    private val klapHosts = ConcurrentHashMap<String, Boolean>()

    suspend fun turnOff(plug: Plug): Boolean = switch(plug, on = false)
    suspend fun turnOn(plug: Plug): Boolean = switch(plug, on = true)

    private suspend fun switch(plug: Plug, on: Boolean): Boolean = withContext(Dispatchers.IO) {
        when (plug.type) {
            PlugType.WEBHOOK -> {
                val url = (if (on) plug.onUrl else plug.offUrl)?.trim().orEmpty()
                if (url.isEmpty()) false else httpGet(url)
            }
            PlugType.TASMOTA -> {
                val host = privateHost(plug.host) ?: return@withContext false
                httpGet("http://$host/cm?cmnd=Power%20${if (on) "On" else "Off"}")
            }
            PlugType.SHELLY -> {
                val host = privateHost(plug.host) ?: return@withContext false
                // Gen2 RPC first; fall back to Gen1 relay endpoint.
                httpGet("http://$host/rpc/Switch.Set?id=0&on=$on") ||
                    httpGet("http://$host/relay/0?turn=${if (on) "on" else "off"}")
            }
            PlugType.KASA -> {
                val host = privateHost(plug.host) ?: return@withContext false
                kasaSwitch(host, on)
            }
        }
    }

    private fun privateHost(host: String?): String? {
        val h = host?.trim()?.substringBefore(':')?.takeIf { it.isNotEmpty() } ?: return null
        // Cut power only toward private/Tailscale addresses — never a public host.
        return if (MinerHostValidator.resolvesToAllowed(h)) h else null
    }

    private fun httpGet(url: String): Boolean = runCatching {
        okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private fun httpGetBody(url: String): String? = runCatching {
        okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    }.getOrNull()

    /** Instantaneous active power (watts) from a metering plug, or null. See [readMeter]. */
    suspend fun readPowerW(plug: Plug): Double? = readMeter(plug)?.powerW

    /** Outcome of a meter read with the reason when it fails (for the plug card's diagnostic). */
    sealed interface MeterResult {
        data class Reading(val reading: PlugReading) : MeterResult
        data class Failure(val reason: String) : MeterResult
    }

    /** Like [readMeter], but says why nothing came back. */
    suspend fun readMeterDetailed(plug: Plug): MeterResult = withContext(Dispatchers.IO) {
        val host = privateHost(plug.host)
        when {
            plug.type == PlugType.WEBHOOK -> MeterResult.Failure("Webhook plugs have no meter.")
            host == null -> MeterResult.Failure("No plug address, or it is not a private/LAN address.")
            plug.type == PlugType.KASA -> kasaMeterDetailed(host)
            else -> readMeter(plug)?.let { MeterResult.Reading(it) }
                ?: MeterResult.Failure("Plug did not answer its metering endpoint (no energy monitor, or unreachable).")
        }
    }

    /**
     * Read the plug's meter: active power plus its energy counters. Null when the plug
     * type can't meter, isn't reachable, or (Kasa KLAP) no TP-Link account is saved.
     *  - Tasmota:  GET /cm?cmnd=Status%208 -> StatusSNS.ENERGY {Power W, Today kWh, Total kWh}
     *  - Shelly:   /rpc/Switch.GetStatus?id=0 (Gen2: apower W, aenergy.total Wh),
     *              else /meter/0 (Gen1: power W, total watt-minutes)
     *  - Kasa:     legacy emeter get_realtime (power_mw, total_wh) or KLAP get_energy_usage
     *              (current_power mW, today_energy Wh, month_energy Wh)
     */
    suspend fun readMeter(plug: Plug): PlugReading? = withContext(Dispatchers.IO) {
        val reading = when (plug.type) {
            PlugType.WEBHOOK -> null
            PlugType.TASMOTA -> {
                val host = privateHost(plug.host) ?: return@withContext null
                httpGetBody("http://$host/cm?cmnd=Status%208")?.let { parseTasmotaStatus8(it) }
            }
            PlugType.SHELLY -> {
                val host = privateHost(plug.host) ?: return@withContext null
                httpGetBody("http://$host/rpc/Switch.GetStatus?id=0")?.let { parseShellyGen2(it) }
                    ?: httpGetBody("http://$host/meter/0")?.let { parseShellyGen1(it) }
            }
            PlugType.KASA -> {
                val host = privateHost(plug.host) ?: return@withContext null
                kasaMeter(host)
            }
        }
        reading?.takeIf { it.powerW != null || it.energyTodayWh != null || it.energyTotalWh != null }
    }

    // ---------------------------------------------------------------- Kasa ----

    private suspend fun kasaSwitch(host: String, on: Boolean): Boolean {
        if (klapHosts[host] != true && kasaSetRelay(host, on)) return true
        val creds = settings.kasaCredentials() ?: return false
        val body = KlapClient.method("set_device_info", "{\"device_on\":$on}")
        return when (klap.request(host, creds, body)) {
            is KlapClient.Result.Ok -> { klapHosts[host] = true; true }
            else -> false
        }
    }

    private suspend fun kasaMeter(host: String): PlugReading? =
        (kasaMeterDetailed(host) as? MeterResult.Reading)?.reading

    private suspend fun kasaMeterDetailed(host: String): MeterResult {
        if (klapHosts[host] != true) {
            kasaExchange(host, """{"emeter":{"get_realtime":{}}}""")?.let { reply ->
                return parseKasaRealtime(reply)?.let { MeterResult.Reading(it) }
                    ?: MeterResult.Failure("Legacy Kasa plug answered but has no energy monitor: $reply")
            }
        }
        val creds = settings.kasaCredentials()
            ?: return MeterResult.Failure(
                "Plug did not answer the legacy Kasa port; newer Kasa (KP125M/Tapo) need your " +
                    "TP-Link account under Settings → Smart plugs.",
            )
        return when (val r = klap.request(host, creds, KlapClient.method("get_energy_usage"))) {
            is KlapClient.Result.Ok -> {
                klapHosts[host] = true
                parseKasaEnergyUsage(r.raw)?.let { MeterResult.Reading(it) }
                    ?: MeterResult.Failure("KLAP plug answered without power fields: ${r.raw}")
            }
            KlapClient.Result.AuthFailed -> MeterResult.Failure(
                "Plug rejected the TP-Link account (handshake) — check e-mail/password, " +
                    "and that this plug is in that Kasa account.",
            )
            is KlapClient.Result.Error -> MeterResult.Failure("KLAP request failed: ${r.cause}")
        }
    }

    /** Kasa local protocol: 4-byte length prefix + autokey-XOR-encrypted JSON on TCP 9999. */
    private fun kasaSetRelay(host: String, on: Boolean): Boolean = runCatching {
        val payload = """{"system":{"set_relay_state":{"state":${if (on) 1 else 0}}}}"""
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, KASA_LEGACY_PORT), KASA_TIMEOUT_MS)
            socket.soTimeout = KASA_TIMEOUT_MS
            socket.getOutputStream().apply { write(kasaEncrypt(payload)); flush() }
            // Read the length-prefixed reply so the plug commits the command.
            val din = DataInputStream(socket.getInputStream())
            val len = din.readInt()
            if (len in 1..KASA_MAX_REPLY) { val buf = ByteArray(len); din.readFully(buf) }
        }
        true
    }.getOrDefault(false)

    /** Send an encrypted Kasa command and return the decrypted JSON reply, or null. */
    private fun kasaExchange(host: String, payload: String): String? = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, KASA_LEGACY_PORT), KASA_TIMEOUT_MS)
            socket.soTimeout = KASA_TIMEOUT_MS
            socket.getOutputStream().apply { write(kasaEncrypt(payload)); flush() }
            val din = DataInputStream(socket.getInputStream())
            val len = din.readInt()
            if (len !in 1..KASA_MAX_REPLY) return null
            val buf = ByteArray(len); din.readFully(buf)
            kasaDecrypt(buf)
        }
    }.getOrNull()

    private fun kasaDecrypt(bytes: ByteArray): String {
        val out = ByteArray(bytes.size)
        var key = KASA_XOR_KEY
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and BYTE_MASK
            out[i] = (key xor b).toByte()
            key = b
        }
        return String(out, Charsets.UTF_8)
    }

    private fun kasaEncrypt(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val out = ByteArray(bytes.size + 4)
        // 4-byte big-endian length header.
        out[0] = (bytes.size ushr 24).toByte(); out[1] = (bytes.size ushr 16).toByte()
        out[2] = (bytes.size ushr 8).toByte(); out[3] = bytes.size.toByte()
        var key = KASA_XOR_KEY
        for (i in bytes.indices) {
            val enc = key xor bytes[i].toInt()
            out[i + 4] = enc.toByte()
            key = enc and BYTE_MASK
        }
        return out
    }

    companion object {
        private const val KASA_LEGACY_PORT = 9999
        private const val KASA_TIMEOUT_MS = 3000
        private const val KASA_MAX_REPLY = 8192
        private const val KASA_XOR_KEY = 171
        private const val BYTE_MASK = 0xFF
        private const val MILLI = 1000.0
        private const val MINUTES_PER_HOUR = 60.0

        private fun num(body: String, key: String): Double? =
            Regex("\"$key\"\\s*:\\s*(-?[0-9.]+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()

        /** Legacy Kasa `emeter.get_realtime`: newer firmware reports *_mw/_wh, older W/kWh. */
        fun parseKasaRealtime(body: String): PlugReading? {
            if (num(body, "err_code")?.let { it != 0.0 } == true) return null
            val watts = num(body, "power_mw")?.div(MILLI) ?: num(body, "power")
            val totalWh = num(body, "total_wh") ?: num(body, "total")?.times(MILLI)
            return PlugReading(powerW = watts?.takeIf { it >= 0 }, energyTotalWh = totalWh)
        }

        /** KLAP `get_energy_usage` result: current_power in mW, today/month energy in Wh. */
        fun parseKasaEnergyUsage(body: String): PlugReading? {
            val watts = num(body, "current_power")?.div(MILLI) ?: return null
            return PlugReading(
                powerW = watts.takeIf { it >= 0 },
                energyTodayWh = num(body, "today_energy"),
                energyTotalWh = null, // KLAP plugs expose today/month only, no lifetime counter
            )
        }

        /** Tasmota `Status 8` → StatusSNS.ENERGY {Power W, Today kWh, Total kWh}. */
        fun parseTasmotaStatus8(body: String): PlugReading? {
            val watts = num(body, "Power") ?: return null
            return PlugReading(
                powerW = watts.takeIf { it >= 0 },
                energyTodayWh = num(body, "Today")?.times(MILLI),
                energyTotalWh = num(body, "Total")?.times(MILLI),
            )
        }

        /** Shelly Gen2 `Switch.GetStatus`: apower W, aenergy.total Wh. */
        fun parseShellyGen2(body: String): PlugReading? {
            val watts = num(body, "apower") ?: return null
            val total = Regex("\"aenergy\"\\s*:\\s*\\{[^}]*\"total\"\\s*:\\s*(-?[0-9.]+)")
                .find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            return PlugReading(powerW = watts.takeIf { it >= 0 }, energyTotalWh = total)
        }

        /** Shelly Gen1 `/meter/0`: power W, total in watt-minutes. */
        fun parseShellyGen1(body: String): PlugReading? {
            val watts = num(body, "power") ?: return null
            return PlugReading(
                powerW = watts.takeIf { it >= 0 },
                energyTotalWh = num(body, "total")?.div(MINUTES_PER_HOUR),
            )
        }
    }
}
