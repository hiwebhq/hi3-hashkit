package hi3.hashkit.ui.solar

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.integrations.homeassistant.HomeAssistantClient
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class SolarState(
    val baseUrl: String = "",
    val entityId: String = "",
    val tokenConfigured: Boolean = false,
    val resumeWatts: Double = 500.0,
    val curtailWatts: Double = 0.0,
    val surplusW: Double? = null,
    val fleetDrawW: Double = 0.0,
    val onlineMiners: Int = 0,
    val decision: CurtailmentEngine.Action? = null,
    val checking: Boolean = false,
    val applying: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SolarSurplusViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val settingsRepository: SettingsRepository,
    private val homeAssistant: HomeAssistantClient,
    private val repository: MinerRepository,
    private val fleetControl: FleetControl,
) : ViewModel() {

    private val _state = MutableStateFlow(SolarState())
    val state: StateFlow<SolarState> = _state

    init {
        viewModelScope.launch {
            val s = settingsRepository.current()
            _state.value = _state.value.copy(
                baseUrl = s.homeAssistantBaseUrl,
                entityId = s.homeAssistantEntityId,
                tokenConfigured = s.homeAssistantTokenConfigured,
                resumeWatts = s.solarResumeWatts,
                curtailWatts = s.solarCurtailWatts,
            )
        }
    }

    fun saveConfig(baseUrl: String, entityId: String, token: String, resumeW: Double, curtailW: Double) {
        viewModelScope.launch {
            settingsRepository.setHomeAssistantBaseUrl(baseUrl)
            settingsRepository.setHomeAssistantEntityId(entityId)
            settingsRepository.setSolarResumeWatts(resumeW)
            settingsRepository.setSolarCurtailWatts(curtailW)
            if (token.isNotBlank()) settingsRepository.setHomeAssistantToken(token)
            _state.value = _state.value.copy(
                baseUrl = baseUrl, entityId = entityId, resumeWatts = resumeW, curtailWatts = curtailW,
                tokenConfigured = _state.value.tokenConfigured || token.isNotBlank(),
                message = appContext.getString(R.string.vm_saved),
            )
        }
    }

    fun check() {
        if (_state.value.checking) return
        viewModelScope.launch {
            _state.value = _state.value.copy(checking = true, message = null)
            val token = settingsRepository.homeAssistantToken().orEmpty()
            val s = _state.value
            val result = homeAssistant.readWatts(s.baseUrl, token, s.entityId)
            val draw = fleetDrawNow()
            when (result) {
                is HomeAssistantClient.Result.Ok -> {
                    val running = draw.first > 0
                    val decision = CurtailmentEngine.decide(
                        value = result.watts, running = running,
                        resumeThreshold = s.resumeWatts, curtailThreshold = s.curtailWatts,
                        favourRunWhenAbove = true,
                    )
                    _state.value = _state.value.copy(
                        surplusW = result.watts, fleetDrawW = draw.first, onlineMiners = draw.second,
                        decision = decision, checking = false,
                        message = appContext.getString(R.string.vm_solar_read, "${result.rawState}${result.unit?.let { " $it" } ?: ""}"),
                    )
                }
                is HomeAssistantClient.Result.Error -> _state.value = _state.value.copy(
                    checking = false, surplusW = null, decision = null,
                    fleetDrawW = draw.first, onlineMiners = draw.second, message = result.message,
                )
            }
        }
    }

    private suspend fun fleetDrawNow(): Pair<Double, Int> {
        val now = Instant.now()
        val live = repository.observeMinerEntities().first()
            .filter { !it.isDemo }
            .map { repository.toDomain(it, now) }
            .filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
        return live.sumOf { it.lastTelemetry?.powerW?.value ?: 0.0 } to live.size
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
fun SolarSurplusScreen(
    onBack: () -> Unit,
    viewModel: SolarSurplusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var baseUrl by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }
    var entityId by remember(state.entityId) { mutableStateOf(state.entityId) }
    var token by remember { mutableStateOf("") }
    var resumeW by remember(state.resumeWatts) { mutableStateOf(state.resumeWatts.toInt().toString()) }
    var curtailW by remember(state.curtailWatts) { mutableStateOf(state.curtailWatts.toInt().toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.solar_title), fontWeight = FontWeight.Bold) },
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
                    stringResource(R.string.solar_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }

            // Live signal card
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
                        Text(stringResource(R.string.solar_export_label), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                        Text(
                            state.surplusW?.let { "%,.0f W".format(it) } ?: "—",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = HiBrand.textPrimary,
                        )
                        Text(
                            stringResource(R.string.solar_fleet_draw, "%,.0f".format(state.fleetDrawW), state.onlineMiners),
                            style = MaterialTheme.typography.labelSmall,
                            color = HiBrand.textSecondary,
                        )
                        state.decision?.let { d ->
                            Text(
                                when (d) {
                                    CurtailmentEngine.Action.RUN -> stringResource(R.string.solar_reco_run)
                                    CurtailmentEngine.Action.CURTAIL -> stringResource(R.string.solar_reco_curtail)
                                    CurtailmentEngine.Action.HOLD -> stringResource(R.string.solar_reco_hold)
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
                    Button(onClick = viewModel::check, enabled = !state.checking) {
                        Text(if (state.checking) stringResource(R.string.solar_checking) else stringResource(R.string.solar_check_now))
                    }
                    if (state.checking) CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = viewModel::applyResume, enabled = !state.applying, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.solar_resume_fleet))
                    }
                    OutlinedButton(onClick = viewModel::applyPause, enabled = !state.applying, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.solar_curtail_fleet))
                    }
                }
            }

            item { Text("HOME ASSISTANT", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
            item {
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.solar_base_url_label)) },
                    placeholder = { Text("http://homeassistant.local:8123") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = entityId, onValueChange = { entityId = it },
                    label = { Text(stringResource(R.string.solar_entity_label)) },
                    placeholder = { Text("sensor.solar_surplus_power") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    label = {
                        Text(
                            if (state.tokenConfigured) stringResource(R.string.solar_token_saved_label)
                            else stringResource(R.string.solar_token_label),
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = resumeW, onValueChange = { resumeW = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.solar_resume_label)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = curtailW, onValueChange = { curtailW = it.filter { c -> c.isDigit() || c == '-' } },
                        label = { Text(stringResource(R.string.solar_curtail_label)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.saveConfig(
                            baseUrl.trim(), entityId.trim(), token,
                            resumeW.toDoubleOrNull() ?: 500.0, curtailW.toDoubleOrNull() ?: 0.0,
                        )
                        token = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.solar_save_config)) }
            }

            state.message?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
            }
        }
    }
}
