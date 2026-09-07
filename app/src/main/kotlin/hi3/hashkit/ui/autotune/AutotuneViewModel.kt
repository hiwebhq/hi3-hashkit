package hi3.hashkit.ui.autotune

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.core.Units
import hi3.hashkit.data.repo.ControlRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.adapter.ActionResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One measured operating point from the sweep. */
data class TuneResult(
    val frequencyMhz: Int,
    val voltageMv: Int,
    val hashrateGhs: Double?,
    val powerW: Double?,
    val efficiencyJTh: Double?,
)

data class AutotuneUiState(
    val supported: Boolean = true,
    val running: Boolean = false,
    val stepIndex: Int = 0,
    val stepTotal: Int = 0,
    val currentLabel: String = "",
    val results: List<TuneResult> = emptyList(),
    val bestFrequencyMhz: Int? = null,
    val message: String? = null,
    /** Original setpoint we restore to unless the user applies a result. */
    val originalFrequencyMhz: Int? = null,
    val originalVoltageMv: Int? = null,
)

/**
 * Bitaxe efficiency autotuner. Sweeps the firmware-approved frequency options at the
 * device's current voltage, lets each point settle, samples hashrate + power → J/TH, then
 * restores the original setpoint and recommends the most efficient stable point. Applying
 * a result is an explicit, separate action.
 */
@HiltViewModel
class AutotuneViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MinerRepository,
    private val controlRepository: ControlRepository,
) : ViewModel() {

    private val minerId: Long = checkNotNull(savedStateHandle["minerId"])
    private val _state = MutableStateFlow(AutotuneUiState())
    val state: StateFlow<AutotuneUiState> = _state
    private var job: Job? = null

    /** Settle time per step, in seconds — ASICs need time to reach steady hashrate/temp. */
    fun start(settleSeconds: Int) {
        if (_state.value.running) return
        job = viewModelScope.launch {
            val entity = repository.observeMinerEntity(minerId).first()
            if (entity == null) { _state.value = _state.value.copy(message = "Miner not found."); return@launch }
            val options = controlRepository.tuneOptions(entity)
            if (options == null || options.frequencyOptionsMhz.isEmpty()) {
                _state.value = _state.value.copy(supported = false, message = "This miner does not expose tunable options.")
                return@launch
            }
            val telemetry = repository.latestTelemetry(entity.id)
            val voltage = telemetry?.coreVoltageMv?.value?.toInt()
                ?: options.defaultVoltageMv ?: options.voltageOptionsMv.firstOrNull()
                ?: run { _state.value = _state.value.copy(message = "No core voltage to hold constant."); return@launch }
            val origFreq = telemetry?.frequencyMhz?.value?.toInt() ?: options.defaultFrequencyMhz
            val candidates = options.frequencyOptionsMhz.sorted()

            _state.value = AutotuneUiState(
                running = true, stepTotal = candidates.size,
                originalFrequencyMhz = origFreq, originalVoltageMv = voltage,
                message = "Sweeping ${candidates.size} frequencies at ${voltage} mV…",
            )
            val results = mutableListOf<TuneResult>()
            try {
                candidates.forEachIndexed { i, freq ->
                    if (!isActive) return@forEachIndexed
                    _state.value = _state.value.copy(
                        stepIndex = i + 1,
                        currentLabel = "$freq MHz @ ${voltage} mV — applying & settling ${settleSeconds}s",
                    )
                    val applied = controlRepository.applyTune(entity, freq, voltage)
                    if (applied !is ActionResult.Success) {
                        results += TuneResult(freq, voltage, null, null, null)
                        return@forEachIndexed
                    }
                    // Settle, then take a fresh live sample.
                    repeat(settleSeconds) { if (isActive) delay(1000) }
                    val fresh = runCatching { repository.pollMiner(entity) }.getOrNull()
                    val hr = fresh?.hashrateGhs?.value
                    val pw = fresh?.powerW?.value
                    results += TuneResult(freq, voltage, hr, pw, Units.efficiencyJTh(pw, hr))
                    _state.value = _state.value.copy(results = results.toList())
                }
            } finally {
                // Always restore the original setpoint; the user opts in to any change.
                if (origFreq != null) runCatching { controlRepository.applyTune(entity, origFreq, voltage) }
            }
            val best = results.filter { it.efficiencyJTh != null && (it.hashrateGhs ?: 0.0) > 0 }
                .minByOrNull { it.efficiencyJTh!! }
            _state.value = _state.value.copy(
                running = false,
                results = results.sortedBy { it.efficiencyJTh ?: Double.MAX_VALUE },
                bestFrequencyMhz = best?.frequencyMhz,
                currentLabel = "",
                message = if (best != null)
                    "Best: ${best.frequencyMhz} MHz at %.1f J/TH. Restored your original setpoint.".format(best.efficiencyJTh)
                else "Sweep finished but no valid efficiency reading was collected.",
            )
        }
    }

    fun applyBest() {
        val best = _state.value.bestFrequencyMhz ?: return
        val voltage = _state.value.originalVoltageMv ?: return
        viewModelScope.launch {
            val entity = repository.observeMinerEntity(minerId).first() ?: return@launch
            val result = controlRepository.applyTune(entity, best, voltage)
            _state.value = _state.value.copy(
                message = if (result is ActionResult.Success) "Applied $best MHz @ ${voltage} mV."
                else "Apply failed: ${(result as? ActionResult.Failure)?.message ?: "unsupported"}",
            )
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(running = false, message = "Sweep cancelled — original setpoint restored.")
        // Restore original on cancel.
        val f = _state.value.originalFrequencyMhz; val v = _state.value.originalVoltageMv
        if (f != null && v != null) viewModelScope.launch {
            val entity = repository.observeMinerEntity(minerId).first() ?: return@launch
            runCatching { controlRepository.applyTune(entity, f, v) }
        }
    }
}
