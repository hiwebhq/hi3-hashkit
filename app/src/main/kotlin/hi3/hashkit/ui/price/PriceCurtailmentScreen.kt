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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.R
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
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
            _state.value = _state.value.copy(region = region.uppercase(), resumePence = resume, curtailPence = curtail, message = appContext.getString(R.string.vm_saved))
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
                        message = if (current == null) appContext.getString(R.string.vm_price_no_rate) else null,
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
            val verb = appContext.getString(if (action == PowerAction.PAUSE) R.string.vm_curtailed else R.string.vm_resumed)
            _state.value = _state.value.copy(
                applying = false,
                message = if (plan.skipped.isNotEmpty())
                    appContext.getString(R.string.vm_curtail_result_skipped, verb, ok, plan.supported.size, plan.skipped.size)
                else appContext.getString(R.string.vm_curtail_result, verb, ok, plan.supported.size),
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
                title = { Text(stringResource(R.string.price_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                    stringResource(R.string.price_intro),
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
                        Text(stringResource(R.string.price_now_label), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                        Text(
                            state.currentPence?.let { "%.2f p/kWh".format(it) } ?: "—",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = HiBrand.textPrimary,
                        )
                        if (state.cheapestPence != null) {
                            Text(
                                stringResource(
                                    R.string.price_next6h,
                                    "%.1f".format(state.cheapestPence),
                                    "%.1f".format(state.priciestPence),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = HiBrand.textSecondary,
                            )
                        }
                        state.decision?.let { d ->
                            Text(
                                when (d) {
                                    CurtailmentEngine.Action.RUN -> stringResource(R.string.price_reco_run)
                                    CurtailmentEngine.Action.CURTAIL -> stringResource(R.string.price_reco_curtail)
                                    CurtailmentEngine.Action.HOLD -> stringResource(R.string.price_reco_hold)
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
                        Text(if (state.loading) stringResource(R.string.common_loading) else stringResource(R.string.price_refresh))
                    }
                    if (state.loading) CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = viewModel::applyResume, enabled = !state.applying, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.price_resume_fleet))
                    }
                    OutlinedButton(onClick = viewModel::applyPause, enabled = !state.applying, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.price_curtail_fleet))
                    }
                }
            }

            if (state.upcoming.isNotEmpty()) {
                item { Text(stringResource(R.string.price_next_slots), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
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

            item { Text(stringResource(R.string.price_configuration), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
            item {
                OutlinedTextField(
                    value = region, onValueChange = { region = it.take(1).uppercase() },
                    label = { Text(stringResource(R.string.price_region_label)) },
                    placeholder = { Text(stringResource(R.string.price_region_placeholder)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = resume, onValueChange = { resume = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.price_resume_label)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = curtail, onValueChange = { curtail = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.price_curtail_label)) },
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
                ) { Text(stringResource(R.string.price_save_config)) }
            }

            state.message?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
            }
        }
    }
}
