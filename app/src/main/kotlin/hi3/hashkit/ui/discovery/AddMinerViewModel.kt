package hi3.hashkit.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.data.repo.AddMinerResult
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.discovery.MinerHostValidator
import hi3.hashkit.discovery.MinerScanner
import hi3.hashkit.discovery.NetworkInspector
import hi3.hashkit.discovery.ScanEvent
import hi3.hashkit.discovery.SubnetUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoveredMiner(
    val host: String,
    val label: String,
    val added: Boolean,
)

data class AddMinerUiState(
    val manualHost: String = "",
    val manualMessage: String? = null,
    val manualBusy: Boolean = false,
    val scanCidr: String = "",
    val scanning: Boolean = false,
    val scanProgress: Pair<Int, Int>? = null,
    val scanMessage: String? = null,
    val discovered: List<DiscoveredMiner> = emptyList(),
)

@HiltViewModel
class AddMinerViewModel @Inject constructor(
    private val repository: MinerRepository,
    private val scanner: MinerScanner,
    private val networkInspector: NetworkInspector,
    private val pollingEngine: PollingEngine,
    private val mdnsDiscovery: hi3.hashkit.discovery.MdnsDiscovery,
) : ViewModel() {

    private val _state = MutableStateFlow(AddMinerUiState())
    val state: StateFlow<AddMinerUiState> = _state

    private var scanJob: Job? = null

    init {
        networkInspector.defaultScanCidr()?.let { cidr ->
            _state.value = _state.value.copy(scanCidr = "${cidr.baseIp}/${cidr.prefix}")
        }
    }

    fun onManualHostChange(value: String) {
        _state.value = _state.value.copy(manualHost = value, manualMessage = null)
    }

    fun onScanCidrChange(value: String) {
        _state.value = _state.value.copy(scanCidr = value, scanMessage = null)
    }

    fun addManual() {
        val host = _state.value.manualHost.trim()
        if (host.isEmpty()) return
        if (!MinerHostValidator.resolvesToAllowed(host)) {
            _state.value = _state.value.copy(
                manualMessage = "Only private LAN or Tailscale (100.x) addresses are allowed.",
            )
            return
        }
        _state.value = _state.value.copy(manualBusy = true, manualMessage = null)
        viewModelScope.launch {
            val result = repository.addByHost(host)
            val message = when (result) {
                is AddMinerResult.Added -> "Added — pulling first telemetry…"
                is AddMinerResult.AlreadyKnown -> "Already tracked; address updated."
                is AddMinerResult.NotSupported -> result.message
                is AddMinerResult.Unreachable -> "Unreachable: ${result.message}"
            }
            if (result is AddMinerResult.Added || result is AddMinerResult.AlreadyKnown) {
                pollingEngine.pollAllOnce()
            }
            _state.value = _state.value.copy(manualBusy = false, manualMessage = message)
        }
    }

    fun startScan() {
        if (_state.value.scanning) return
        val cidr = SubnetUtils.parseCidr(_state.value.scanCidr)
        if (cidr == null) {
            _state.value = _state.value.copy(
                scanMessage = "Enter a private CIDR like 192.168.1.0/24.",
            )
            return
        }
        val hosts = SubnetUtils.expand(cidr)
        if (hosts == null) {
            _state.value = _state.value.copy(
                scanMessage = "Range larger than /${SubnetUtils.DEFAULT_MAX_PREFIX} — narrow it before scanning.",
            )
            return
        }
        _state.value = _state.value.copy(
            scanning = true,
            scanMessage = null,
            scanProgress = 0 to hosts.size,
            discovered = emptyList(),
        )
        scanJob = viewModelScope.launch {
            scanner.scan(hosts).collect { event ->
                when (event) {
                    is ScanEvent.Progress ->
                        _state.value = _state.value.copy(scanProgress = event.scanned to event.total)
                    is ScanEvent.Found -> {
                        val result = repository.upsertDiscovered(
                            host = event.host,
                            port = 80,
                            adapterType = event.probe.adapterType,
                            identity = event.probe.identity,
                        )
                        val label = event.probe.identity.hostname
                            ?: event.probe.identity.model
                            ?: event.host
                        _state.value = _state.value.copy(
                            discovered = _state.value.discovered + DiscoveredMiner(
                                host = event.host,
                                label = label,
                                added = result is AddMinerResult.Added,
                            ),
                        )
                    }
                    is ScanEvent.Finished -> {
                        _state.value = _state.value.copy(
                            scanning = false,
                            scanMessage = "Scanned ${event.scanned} hosts, found ${event.found} miner(s).",
                        )
                        pollingEngine.pollAllOnce()
                    }
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _state.value = _state.value.copy(scanning = false, scanMessage = "Scan cancelled.")
    }

    /** mDNS browse for ~10s, probing every resolved private host. */
    fun startMdnsSearch() {
        if (_state.value.scanning) return
        _state.value = _state.value.copy(
            scanning = true, scanMessage = "Searching via mDNS…", scanProgress = null, discovered = emptyList(),
        )
        scanJob = viewModelScope.launch {
            val seen = mutableSetOf<String>()
            val job = launch {
                mdnsDiscovery.discoverHttpHosts().collect { host ->
                    if (!seen.add(host)) return@collect
                    val result = repository.addByHost(host)
                    if (result is AddMinerResult.Added || result is AddMinerResult.AlreadyKnown) {
                        _state.value = _state.value.copy(
                            discovered = _state.value.discovered + DiscoveredMiner(
                                host = host,
                                label = "mDNS device",
                                added = result is AddMinerResult.Added,
                            ),
                        )
                    }
                }
            }
            kotlinx.coroutines.delay(10_000)
            job.cancel()
            _state.value = _state.value.copy(
                scanning = false,
                scanMessage = "mDNS search finished: ${_state.value.discovered.size} miner(s).",
            )
            pollingEngine.pollAllOnce()
        }
    }
}
