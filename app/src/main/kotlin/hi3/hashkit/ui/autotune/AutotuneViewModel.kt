package hi3.hashkit.ui.autotune

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.core.Units
import hi3.hashkit.data.db.TuneSweepDao
import hi3.hashkit.data.db.TuneSweepEntity
import hi3.hashkit.data.repo.ControlRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.domain.tune.TuneOptimizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val chipTempC: Double? = null,
    /** True when this point exceeded the thermal ceiling (excluded from "best"). */
    val overTemp: Boolean = false,
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
    /** What "best" optimizes for (false = efficiency / min J·TH⁻¹, true = max hashrate). */
    val optimizeForHashrate: Boolean = false,
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
    private val tuneSweepDao: TuneSweepDao,
    private val telemetryDao: hi3.hashkit.data.db.TelemetryDao,
) : ViewModel() {

    private val minerId: Long = checkNotNull(savedStateHandle["minerId"])
    private val _state = MutableStateFlow(AutotuneUiState())
    val state: StateFlow<AutotuneUiState> = _state
    private var job: Job? = null

    /** What each observed operating point actually delivered, plus peer standing. */
    data class TuneInsightsUi(
        val periods: List<hi3.hashkit.data.db.SettingsPeriodStat>,
        val bestEfficiency: hi3.hashkit.data.db.SettingsPeriodStat?,
        val bestHashrate: hi3.hashkit.data.db.SettingsPeriodStat?,
        /** Percent vs the median same-model peer over 24h; negative = behind. */
        val peerGapPercent: Double?,
        val peerCount: Int,
    )

    private val _insights = MutableStateFlow<TuneInsightsUi?>(null)
    val insights: StateFlow<TuneInsightsUi?> = _insights

    init {
        viewModelScope.launch { runCatching { loadInsights() } }
    }

    private suspend fun loadInsights() {
        val now = System.currentTimeMillis()
        val periods = telemetryDao.settingsPeriods(
            minerId, now - INSIGHTS_WINDOW_MS, INSIGHTS_MIN_SAMPLES,
        )
        val me = repository.observeMinerEntity(minerId).first() ?: return
        val ranked = hi3.hashkit.domain.tune.TuneInsights.rank(periods, me.alertChipTempC)
        val daySince = now - PEER_WINDOW_MS
        val peers = repository.observeMinerEntities().first().filter {
            it.id != minerId && !it.isDemo && it.model != null && it.model == me.model
        }
        val peerAvgs = peers.mapNotNull { repository.avgHashrateSince(it.id, daySince) }
        val gap = hi3.hashkit.domain.tune.TuneInsights.peerGapPercent(
            repository.avgHashrateSince(minerId, daySince), peerAvgs,
        )
        // Publish only when there's something to show; the screen renders nothing for null.
        if (ranked.periods.isNotEmpty() || gap != null) {
            _insights.value = TuneInsightsUi(
                periods = ranked.periods,
                bestEfficiency = ranked.bestEfficiency,
                bestHashrate = ranked.bestHashrate,
                peerGapPercent = gap,
                peerCount = peerAvgs.size,
            )
        }
    }


    /** Persisted efficiency curve across all past sweeps for this miner. */
    val optimizer: StateFlow<TuneOptimizer.Summary> =
        tuneSweepDao.observeForMiner(minerId)
            .map { rows ->
                TuneOptimizer.summarize(
                    rows.map {
                        TuneOptimizer.Point(
                            frequencyMhz = it.frequencyMhz,
                            voltageMv = it.voltageMv,
                            hashrateGhs = it.hashrateGhs,
                            powerW = it.powerW,
                            efficiencyJTh = it.efficiencyJTh,
                            chipTempC = it.chipTempC,
                            overTemp = it.overTemp,
                        )
                    }
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TuneOptimizer.summarize(emptyList()))

    fun clearHistory() {
        viewModelScope.launch { tuneSweepDao.deleteForMiner(minerId) }
    }

    /**
     * @param settleSeconds settle time per step — ASICs need time to reach steady state.
     * @param maxChipTempC thermal ceiling: a point that settles above this is excluded, and
     *   the sweep stops climbing (higher frequencies only run hotter).
     * @param optimizeForHashrate pick the highest-hashrate safe point instead of the most efficient.
     */
    fun start(settleSeconds: Int, maxChipTempC: Int, optimizeForHashrate: Boolean) {
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
            // Hold voltage constant at an APPROVED setpoint. Telemetry reports the live *measured*
            // core voltage (e.g. 1245 mV), which drifts off the firmware's approved list — snap it
            // to the nearest allowed option so applyTune isn't rejected every step.
            val rawVoltage = telemetry?.coreVoltageMv?.value?.toInt()
                ?: options.defaultVoltageMv ?: options.voltageOptionsMv.firstOrNull()
                ?: run { _state.value = _state.value.copy(message = "No core voltage to hold constant."); return@launch }
            val voltage = nearestOption(rawVoltage, options.voltageOptionsMv)
            // Snap the original frequency to an approved option too, so the restore never fails.
            val rawFreq = telemetry?.frequencyMhz?.value?.toInt() ?: options.defaultFrequencyMhz
            val origFreq = rawFreq?.let { nearestOption(it, options.frequencyOptionsMhz) } ?: rawFreq
            val candidates = options.frequencyOptionsMhz.sorted()

            _state.value = AutotuneUiState(
                running = true, stepTotal = candidates.size,
                originalFrequencyMhz = origFreq, originalVoltageMv = voltage,
                optimizeForHashrate = optimizeForHashrate,
                message = "Sweeping ${candidates.size} frequencies at ${voltage} mV, ceiling ${maxChipTempC}°C…",
            )
            val results = mutableListOf<TuneResult>()
            val sweepStart = System.currentTimeMillis()
            var stoppedForHeat = false
            try {
                for ((i, freq) in candidates.withIndex()) {
                    if (!isActive) break
                    _state.value = _state.value.copy(
                        stepIndex = i + 1,
                        currentLabel = "$freq MHz @ ${voltage} mV — applying & settling ${settleSeconds}s",
                    )
                    val applied = controlRepository.applyTune(entity, freq, voltage)
                    if (applied !is ActionResult.Success) {
                        results += TuneResult(freq, voltage, null, null, null)
                        _state.value = _state.value.copy(results = results.toList())
                        continue
                    }
                    // Settle, then take a fresh live sample.
                    repeat(settleSeconds) { if (isActive) delay(1000) }
                    val fresh = runCatching { repository.pollMiner(entity) }.getOrNull()
                    val hr = fresh?.hashrateGhs?.value
                    val pw = fresh?.powerW?.value
                    val temp = fresh?.chipTempC?.value
                    val over = temp != null && temp > maxChipTempC
                    results += TuneResult(freq, voltage, hr, pw, Units.efficiencyJTh(pw, hr), temp, over)
                    _state.value = _state.value.copy(results = results.toList())
                    // Higher frequencies only run hotter — stop climbing once over the ceiling.
                    if (over) { stoppedForHeat = true; break }
                }
            } finally {
                // Always restore the original setpoint; the user opts in to any change.
                if (origFreq != null) runCatching { controlRepository.applyTune(entity, origFreq, voltage) }
            }
            // Persist the measured points so the optimizer curve survives across sweeps.
            if (results.isNotEmpty()) runCatching {
                val at = System.currentTimeMillis()
                tuneSweepDao.insertAll(
                    results.map { r ->
                        TuneSweepEntity(
                            minerId = minerId, sweepStartEpochMs = sweepStart, atEpochMs = at,
                            frequencyMhz = r.frequencyMhz, voltageMv = r.voltageMv,
                            hashrateGhs = r.hashrateGhs, powerW = r.powerW,
                            efficiencyJTh = r.efficiencyJTh, chipTempC = r.chipTempC, overTemp = r.overTemp,
                        )
                    }
                )
            }
            val safe = results.filter { !it.overTemp && (it.hashrateGhs ?: 0.0) > 0 && it.efficiencyJTh != null }
            val best = if (optimizeForHashrate) safe.maxByOrNull { it.hashrateGhs!! }
            else safe.minByOrNull { it.efficiencyJTh!! }
            val sorted = if (optimizeForHashrate)
                results.sortedByDescending { it.hashrateGhs ?: -1.0 }
            else results.sortedBy { it.efficiencyJTh ?: Double.MAX_VALUE }
            _state.value = _state.value.copy(
                running = false,
                results = sorted,
                bestFrequencyMhz = best?.frequencyMhz,
                currentLabel = "",
                message = buildString {
                    if (stoppedForHeat) append("Stopped early: hit the ${maxChipTempC}°C ceiling. ")
                    append(
                        when {
                            best == null -> "No safe point produced a valid reading."
                            optimizeForHashrate -> "Best hashrate: ${best.frequencyMhz} MHz (${Units.formatHashrate(best.hashrateGhs)})."
                            else -> "Best efficiency: ${best.frequencyMhz} MHz at %.1f J/TH.".format(best.efficiencyJTh)
                        }
                    )
                    append(" Restored your original setpoint.")
                },
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

    companion object {
        /**
         * Snap [value] to the nearest firmware-approved option. The auto-tuner holds voltage at a
         * measured value that drifts off the approved set (e.g. 1245 vs the allowed 1250); without
         * snapping, applyTune rejects every step. Returns [value] unchanged if [options] is empty.
         */
        fun nearestOption(value: Int, options: List<Int>): Int =
            options.minByOrNull { kotlin.math.abs(it - value) } ?: value

        private const val INSIGHTS_WINDOW_MS = 7L * 86_400_000L
        /** ~10 minutes at 15 s polling before an operating point counts. */
        private const val INSIGHTS_MIN_SAMPLES = 40
        private const val PEER_WINDOW_MS = 86_400_000L
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
