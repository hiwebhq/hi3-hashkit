package hi3.hashkit.ui.price

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.FleetControl
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.adapter.PowerAction
import hi3.hashkit.domain.curtail.CurtailmentEngine
import hi3.hashkit.integrations.energy.OctopusAgileClient
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class PriceSlot(val label: String, val pence: Double, val current: Boolean)

data class PriceState(
    val region: String = "",
    val resumePence: Double = 15.0,
    val curtailPence: Double = 30.0,
    val currentPence: Double? = null,
    val upcoming: List<PriceSlot> = emptyList(),
    val cheapestPence: Double? = null,
    val priciestPence: Double? = null,
    val decision: CurtailmentEngine.Action? = null,
    val loading: Boolean = false,
    val applying: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class PriceCurtailmentViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val octopus: OctopusAgileClient,
    private val repository: MinerRepository,
    private val fleetControl: FleetControl,
) : ViewModel() {

    private val _state = MutableStateFlow(PriceState())
    val state: StateFlow<PriceState> = _state

    private val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    init {
        viewModelScope.launch {
            val s = settingsRepository.current()
            _state.value = _state.value.copy(
                region = s.octopusRegion, resumePence = s.priceResumePence, curtailPence = s.priceCurtailPence,
            )
            if (s.octopusRegion.isNotBlank()) refresh()
        }
    }

    fun saveConfig(region: String, resume: Double, curtail: Double) {
        viewModelScope.launch {
            settingsRepository.setOctopusRegion(region)
            settingsRepository.setPriceResumePence(resume)
            settingsRepository.setPriceCurtailPence(curtail)
            _state.value = _state.value.copy(region = region.uppercase(), resumePence = resume, curtailPence = curtail, message = "Saved.")
            if (region.isNotBlank()) refresh()
        }
    }

    fun refresh() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            when (val r = octopus.fetch(_state.value.region)) {
                is OctopusAgileClient.Result.Ok -> {
                    val now = Instant.now()
                    val current = OctopusAgileClient.currentRate(r.rates, now)
                    val upcoming = OctopusAgileClient.upcoming(r.rates, now).take(12)
                    val running = fleetRunning()
                    val decision = current?.let {
                        CurtailmentEngine.decide(
                            value = it.pencePerKwh, running = running,
                            resumeThreshold = _state.value.resumePence, curtailThreshold = _state.value.curtailPence,
                            favourRunWhenAbove = false,
                        )
                    }
                    _state.value = _state.value.copy(
                        loading = false,
                        currentPence = current?.pencePerKwh,
                        upcoming = upcoming.map {
                            PriceSlot(fmt.format(it.validFrom), it.pencePerKwh, it == current)
                        },
                        cheapestPence = upcoming.minOfOrNull { it.pencePerKwh },
                        priciestPence = upcoming.maxOfOrNull { it.pencePerKwh },
                        decision = decision,
                        message = if (current == null) "No rate covers the current half-hour yet." else null,
                    )
                }
                is OctopusAgileClient.Result.Error -> _state.value = _state.value.copy(loading = false, message = r.message)
            }
        }
    }

    private suspend fun fleetRunning(): Boolean {
        val now = Instant.now()
        return repository.observeMinerEntities().first()
            .filter { !it.isDemo }
            .map { repository.toDomain(it, now) }
            .any { (it.lastTelemetry?.powerW?.value ?: 0.0) > 0 }
    }

    fun applyPause() = apply(PowerAction.PAUSE)
    fun applyResume() = apply(PowerAction.RESUME)

    private fun apply(action: PowerAction) {
        if (_state.value.applying) return
        viewModelScope.launch {
            _state.value = _state.value.copy(applying = true, message = null)
            val targets = repository.observeMinerEntities().first().filter { !it.isDemo }
            val plan = fleetControl.plan(hi3.hashkit.data.repo.BulkAction.Power(action), targets)
            val outcomes = fleetControl.execute(plan)
            val ok = outcomes.count { it.result is hi3.hashkit.domain.adapter.ActionResult.Success }
            _state.value = _state.value.copy(
                applying = false,
                message = "${if (action == PowerAction.PAUSE) "Curtailed" else "Resumed"} $ok/${plan.supported.size} miner(s)" +
                    (if (plan.skipped.isNotEmpty()) ", ${plan.skipped.size} skipped." else "."),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceCurtailmentScreen(
    onBack: () -> Unit,
    viewModel: PriceCurtailmentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var region by remember(state.region) { mutableStateOf(state.region) }
    var resume by remember(state.resumePence) { mutableStateOf(state.resumePence.toString()) }
    var curtail by remember(state.curtailPence) { mutableStateOf(state.curtailPence.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Price curtailment", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        containerColor = HiBrand.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Pause on price spikes and mine when power is cheap, using the free Octopus " +
                        "Agile half-hourly feed (UK). No account or key needed — only the public " +
                        "price for your region is fetched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (state.decision) {
                            CurtailmentEngine.Action.RUN -> HiBrand.statusOnline.copy(alpha = 0.15f)
                            CurtailmentEngine.Action.CURTAIL -> HiBrand.statusOffline.copy(alpha = 0.15f)
                            else -> HiBrand.surface
                        },
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("PRICE NOW", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                        Text(
                            state.currentPence?.let { "%.2f p/kWh".format(it) } ?: "—",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = HiBrand.textPrimary,
                        )
                        if (state.cheapestPence != null) {
                            Text(
                                "Next 6h: low ${"%.1f".format(state.cheapestPence)} · high ${"%.1f".format(state.priciestPence)} p/kWh",
                                style = MaterialTheme.typography.labelSmall,
                                color = HiBrand.textSecondary,
                            )
                        }
                        state.decision?.let { d ->
                            Text(
                                when (d) {
                                    CurtailmentEngine.Action.RUN -> "Recommendation: cheap — resume mining"
                                    CurtailmentEngine.Action.CURTAIL -> "Recommendation: expensive — curtail mining"
                                    CurtailmentEngine.Action.HOLD -> "Recommendation: hold (within the deadband)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = HiBrand.textPrimary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = viewModel::refresh, enabled = !state.loading) {
                        Text(if (state.loading) "Loading…" else "Refresh prices")
                    }
                    if (state.loading) CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = viewModel::applyResume, enabled = !state.applying, modifier = Modifier.weight(1f)) {
                        Text("Resume fleet")
                    }
                    OutlinedButton(onClick = viewModel::applyPause, enabled = !state.applying, modifier = Modifier.weight(1f)) {
                        Text("Curtail fleet")
                    }
                }
            }

            if (state.upcoming.isNotEmpty()) {
                item { Text("NEXT SLOTS", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
                for (slot in state.upcoming) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                (if (slot.current) "● " else "") + slot.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (slot.current) HiBrand.accent else HiBrand.textPrimary,
                            )
                            Text(
                                "%.2f p".format(slot.pence),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    slot.pence >= state.curtailPence -> HiBrand.statusOffline
                                    slot.pence <= state.resumePence -> HiBrand.statusOnline
                                    else -> HiBrand.textSecondary
                                },
                            )
                        }
                    }
                }
            }

            item { Text("CONFIGURATION", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
            item {
                OutlinedTextField(
                    value = region, onValueChange = { region = it.take(1).uppercase() },
                    label = { Text("Region letter (A–P)") },
                    placeholder = { Text("C for London") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = resume, onValueChange = { resume = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Resume ≤ p/kWh") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = curtail, onValueChange = { curtail = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Curtail ≥ p/kWh") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.saveConfig(
                            region.trim(), resume.toDoubleOrNull() ?: 15.0, curtail.toDoubleOrNull() ?: 30.0,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save configuration") }
            }

            state.message?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
            }
        }
    }
}
