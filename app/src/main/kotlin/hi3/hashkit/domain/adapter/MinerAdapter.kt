package hi3.hashkit.domain.adapter

import hi3.hashkit.domain.model.MinerCapabilities
import hi3.hashkit.domain.model.MinerIdentity
import hi3.hashkit.domain.model.MinerTelemetry

/**
 * Address of a candidate or known miner. [secret] is the miner's own admin password / API
 * token (decrypted just-in-time by the control layer) for firmware whose controls require
 * authentication (e.g. VNish web API); null for read paths and credential-less controls.
 */
data class MinerHost(val host: String, val port: Int = 80, val secret: String? = null)

sealed interface ProbeResult {
    /** The host answered a known endpoint and looks like a device this adapter supports. */
    data class Supported(
        val adapterType: String,
        val identity: MinerIdentity,
        val rawResponse: String,
    ) : ProbeResult

    /** The host answered but is not a device this adapter recognizes. */
    data object NotThisDevice : ProbeResult

    /** No answer (closed port, timeout, connection error). */
    data class Unreachable(val cause: String) : ProbeResult
}

sealed interface ActionResult {
    data object Success : ActionResult
    data class Failure(val message: String) : ActionResult

    /** The adapter does not support this action for this device/firmware. */
    data class Unsupported(val reason: String) : ActionResult
}

sealed interface TelemetryResult {
    data class Success(val telemetry: MinerTelemetry, val rawResponse: String) : TelemetryResult
    data class Offline(val cause: String) : TelemetryResult
    data class ParseError(val cause: String, val rawResponse: String?) : TelemetryResult
}

/**
 * Read path only. Write/control operations live in [MinerControlAdapter] so that
 * monitoring-only adapters (e.g. Canaan until controls are verified) never expose them.
 */
interface MinerAdapter {
    /** Machine identifier for this adapter family, stored per miner. */
    val adapterType: String

    /** Human name shown in the UI. */
    val displayName: String

    /** Port this adapter's API lives on (80 for HTTP miners, 4028 for CGMiner-API miners). */
    val defaultPort: Int

    suspend fun probe(host: MinerHost): ProbeResult
    suspend fun getIdentity(host: MinerHost): MinerIdentity?
    suspend fun getTelemetry(host: MinerHost): TelemetryResult
    fun getCapabilities(identity: MinerIdentity?): MinerCapabilities

    /**
     * Adapter-specific derived health-event lines fetched alongside a poll (e.g. Avalon's
     * `notify` counter diffs), for firmwares with no log stream. Lines follow the derived
     * event-line wording (see LogEventDeriver) so the classifier/analyzer understand them.
     * Default: none.
     */
    suspend fun healthEventLines(host: MinerHost): List<String> = emptyList()
}

/**
 * Firmware-approved tuning options, fetched from the device itself. Tuning is offered
 * only when the device reports these; values outside the lists are always rejected.
 */
data class TuneOptions(
    val frequencyOptionsMhz: List<Int>,
    val voltageOptionsMv: List<Int>,
    val defaultFrequencyMhz: Int?,
    val defaultVoltageMv: Int?,
)

/** Fan configuration: automatic (with optional target temp) or a fixed manual percent. */
sealed interface FanControl {
    data class Automatic(val targetTempC: Int? = null) : FanControl
    data class Manual(val percent: Int) : FanControl
}

/** Soft power control: pause or resume hashing without a full reboot. */
enum class PowerAction { PAUSE, RESUME }

/**
 * Write path. Implemented only where a control endpoint is verified against real
 * firmware. Every call must be gated on [MinerAdapter.getCapabilities] by the caller,
 * and adapters must still return [ActionResult.Unsupported] defensively.
 */
interface MinerControlAdapter : MinerAdapter {
    /** Device-reported safe tune options, or null when the firmware does not publish them. */
    suspend fun getTuneOptions(host: MinerHost): TuneOptions?

    suspend fun reboot(host: MinerHost): ActionResult
    suspend fun setPrimaryPool(host: MinerHost, url: String, port: Int, worker: String): ActionResult
    suspend fun setFan(host: MinerHost, config: FanControl): ActionResult

    /** Apply a tune; values MUST be members of [getTuneOptions] lists. */
    suspend fun applyTune(host: MinerHost, frequencyMhz: Int, coreVoltageMv: Int): ActionResult

    /** Pause/resume hashing. Default: unsupported (most families expose no such control). */
    suspend fun powerControl(host: MinerHost, action: PowerAction): ActionResult =
        ActionResult.Unsupported("This device has no pause/resume control.")
}
