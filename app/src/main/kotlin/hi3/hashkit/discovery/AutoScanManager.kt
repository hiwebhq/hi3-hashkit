package hi3.hashkit.discovery

import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.AddMinerResult
import hi3.hashkit.data.repo.FarmRepository
import hi3.hashkit.data.repo.MinerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-level background scanner for the local subnet. It runs independently of any screen
 * so a scan kicked off at launch keeps going while the user reads the dashboard — by the
 * time they open Add Miner, LAN devices are already discovered and tracked.
 *
 * The user controls it directly: start / stop / restart, and change the target CIDR. The
 * device's current LAN IP is surfaced so it's clear which network is being scanned. When a
 * farm/site is active, its subnets are scanned and discovered miners are tagged to it — the
 * same primitive intended to back future ASIC-discovery and log-audit tooling.
 *
 * Same safety envelope as the manual scan: only private/CGNAT hosts are probed, on the
 * adapters' known ports, and each CIDR is capped by [SubnetUtils.DEFAULT_MAX_PREFIX].
 */
@Singleton
class AutoScanManager @Inject constructor(
    private val scanner: MinerScanner,
    private val repository: MinerRepository,
    private val pollingEngine: PollingEngine,
    private val networkInspector: NetworkInspector,
    private val settingsRepository: SettingsRepository,
    private val farmRepository: FarmRepository,
) {
    data class State(
        /** The device's current LAN IPv4 address, or null when off-network. */
        val localIp: String? = null,
        /** Target range, e.g. "10.0.0.0/24". Empty until a network is detected. */
        val cidr: String = "",
        val running: Boolean = false,
        val scanned: Int = 0,
        val total: Int = 0,
        val found: Int = 0,
        val message: String? = null,
        /** Farm being populated by the current/last scan, if any. */
        val farmId: Long? = null,
        val farmName: String? = null,
        /** Wall-clock of the last completed scan, for an honest "last scan" line. */
        val lastFinishedAtMs: Long? = null,
    )

    private val scope = CoroutineScope(SupervisorJob())
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var scanJob: Job? = null

    /** Refresh the detected IP/CIDR without starting a scan (call when a screen opens). */
    fun refreshNetwork() {
        val ip = networkInspector.localPrivateAddresses().firstOrNull()
        val detected = networkInspector.defaultScanCidr()?.let { "${it.baseIp}/${it.prefix}" }
        _state.value = _state.value.copy(
            localIp = ip,
            cidr = _state.value.cidr.ifBlank { detected ?: "" },
        )
    }

    /** Start a scan only if one isn't already running — the launch-time entry point. */
    fun startIfEnabled() {
        scope.launch {
            if (!settingsRepository.current().autoScanOnStartup) return@launch
            // Prefer the active farm's subnets; fall back to the detected /24.
            val farm = farmRepository.activeOrDefaultFarm()
            val farmCidr = farm?.subnetsCsv?.split(",")?.map { it.trim() }?.firstOrNull { it.isNotBlank() }
            if (farm != null && farmCidr != null) {
                _state.value = _state.value.copy(cidr = farmCidr, farmId = farm.id, farmName = farm.name)
                start(farmCidr, farm.id, farm.name)
            } else {
                start()
            }
        }
    }

    /**
     * Start a scan. [cidrOverride] targets a specific range (e.g. a farm's subnet); when
     * [assignFarmId] is set, discovered miners are tagged to that farm.
     */
    fun start(cidrOverride: String? = null, assignFarmId: Long? = null, farmName: String? = null) {
        if (_state.value.running) return
        refreshNetwork()
        val target = cidrOverride?.trim()?.ifBlank { null } ?: _state.value.cidr
        val cidr = SubnetUtils.parseCidr(target)
        if (cidr == null) {
            _state.value = _state.value.copy(
                message = if (_state.value.localIp == null)
                    "Not on a Wi-Fi/LAN network — connect and try again."
                else "Enter a private CIDR like 192.168.1.0/24.",
            )
            return
        }
        val hosts = SubnetUtils.expand(cidr)
        if (hosts == null) {
            _state.value = _state.value.copy(
                message = "Range larger than /${SubnetUtils.DEFAULT_MAX_PREFIX} — narrow it first.",
            )
            return
        }
        _state.value = _state.value.copy(
            cidr = target,
            running = true, message = null, scanned = 0, total = hosts.size, found = 0,
            farmId = assignFarmId, farmName = farmName,
        )
        scanJob = scope.launch {
            scanner.scan(hosts).collect { event ->
                when (event) {
                    is ScanEvent.Progress ->
                        _state.value = _state.value.copy(scanned = event.scanned, total = event.total)
                    is ScanEvent.Found -> {
                        val result = repository.upsertDiscovered(
                            host = event.host,
                            port = 0,
                            adapterType = event.probe.adapterType,
                            identity = event.probe.identity,
                        )
                        val minerId = when (result) {
                            is AddMinerResult.Added -> result.minerId
                            is AddMinerResult.AlreadyKnown -> result.minerId
                            else -> null
                        }
                        if (assignFarmId != null && minerId != null) {
                            farmRepository.assignMiner(minerId, assignFarmId)
                        }
                        _state.value = _state.value.copy(found = _state.value.found + 1)
                    }
                    is ScanEvent.Finished -> {
                        _state.value = _state.value.copy(
                            running = false,
                            message = "Found ${event.found} miner(s) across ${event.scanned} hosts.",
                            lastFinishedAtMs = System.currentTimeMillis(),
                        )
                        pollingEngine.pollAllOnce()
                    }
                }
            }
        }
    }

    /** Scan a specific farm's subnets and tag everything found to it. */
    fun scanFarm(farmId: Long) {
        scope.launch {
            val farm = farmRepository.byId(farmId) ?: return@launch
            val cidr = farm.subnetsCsv.split(",").map { it.trim() }.firstOrNull { it.isNotBlank() }
            if (cidr == null) {
                _state.value = _state.value.copy(message = "${farm.name} has no subnet set.")
                return@launch
            }
            start(cidr, farm.id, farm.name)
        }
    }

    fun stop() {
        scanJob?.cancel()
        scanJob = null
        if (_state.value.running) {
            _state.value = _state.value.copy(running = false, message = "Scan stopped.")
        }
    }

    fun restart() {
        val cidr = _state.value.cidr
        val farmId = _state.value.farmId
        val farmName = _state.value.farmName
        stop()
        start(cidr, farmId, farmName)
    }

    /** Change the target range; a running scan is left alone until the user restarts it. */
    fun setCidr(value: String) {
        _state.value = _state.value.copy(cidr = value.trim(), message = null)
    }
}
