package hi3.hashkit.ui.heat

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.heat.HeatReuseMath
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

data class HeatReuseState(
    val livePowerW: Double = 0.0,
    val onlineMiners: Int = 0,
    val ratePerKwh: Double = 0.0,
    val currencyCode: String = "USD",
    val loaded: Boolean = false,
) {
    val btuPerHour: Double? get() = HeatReuseMath.btuPerHour(livePowerW)
    val kwhThermalPerDay: Double? get() = HeatReuseMath.kwhThermalPerDay(livePowerW)
    val kwThermal: Double get() = livePowerW / 1000.0
    val valuePerDay: Double? get() = HeatReuseMath.heatingValuePerDay(livePowerW, ratePerKwh.takeIf { it > 0 })
    val valuePerMonth: Double? get() = HeatReuseMath.heatingValuePerMonth(livePowerW, ratePerKwh.takeIf { it > 0 })
    val valuePerMonthHeatPump: Double?
        get() = HeatReuseMath.heatingValuePerMonthVsHeatPump(livePowerW, ratePerKwh.takeIf { it > 0 }, cop = 3.0)
}

@HiltViewModel
class HeatReuseViewModel @Inject constructor(
    private val repository: MinerRepository,
    private val settingsRepository: SettingsRepository,
    pollingEngine: PollingEngine,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HeatReuseState> =
        kotlinx.coroutines.flow.combine(
            pollingEngine.lastRefresh,
            settingsRepository.settings.map { it.demoModeEnabled },
            settingsRepository.settings.map { it.electricityRatePerKwh to it.currencyCode },
        ) { _, demo, rateCurrency -> demo to rateCurrency }
            .mapLatest { (demo, rateCurrency) ->
                val now = Instant.now()
                val miners = repository.observeMinerEntities().first()
                    .filter { it.isDemo == demo }
                    .map { repository.toDomain(it, now) }
                    .filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
                val power = miners.sumOf { it.lastTelemetry?.powerW?.value ?: 0.0 }
                HeatReuseState(
                    livePowerW = power,
                    onlineMiners = miners.size,
                    ratePerKwh = rateCurrency.first,
                    currencyCode = rateCurrency.second,
                    loaded = true,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeatReuseState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatReuseScreen(
    onBack: () -> Unit,
    viewModel: HeatReuseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Heat reuse", fontWeight = FontWeight.Bold) },
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
                    "An ASIC turns essentially all the electricity it draws into heat. In heating " +
                        "season, that heat offsets what you'd otherwise spend to warm the space — so " +
                        "the power isn't a pure mining cost. Figures are estimates from live fleet power.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }

            item {
                BigCard(
                    label = "Heat output now",
                    value = state.btuPerHour?.let { "%,.0f BTU/hr".format(it) } ?: "—",
                    sub = "${"%.2f".format(state.kwThermal)} kW thermal · ${state.onlineMiners} miner(s) online",
                )
            }
            item {
                BigCard(
                    label = "Thermal energy per day",
                    value = state.kwhThermalPerDay?.let { "%,.1f kWh".format(it) } ?: "—",
                    sub = "At the current draw, sustained for 24 h",
                )
            }

            if (state.loaded && state.ratePerKwh <= 0.0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Set your electricity rate in Settings to see the heat's cash value.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HiBrand.textSecondary,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            } else {
                item {
                    BigCard(
                        label = "Heating value today",
                        value = state.valuePerDay?.let { money(it, state.currencyCode) } ?: "—",
                        sub = "vs. resistive electric heat (1:1) at ${money(state.ratePerKwh, state.currencyCode)}/kWh",
                        accent = true,
                    )
                }
                item {
                    BigCard(
                        label = "Heating value this month",
                        value = state.valuePerMonth?.let { money(it, state.currencyCode) } ?: "—",
                        sub = "≈ ${"%.1f".format(HeatReuseMath.DAYS_PER_MONTH)} days at the current rate of draw",
                        accent = true,
                    )
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "If you heat with a heat pump instead",
                                style = MaterialTheme.typography.titleSmall,
                                color = HiBrand.textPrimary,
                            )
                            Text(
                                "A heat pump (COP ≈ 3) makes the same heat for about a third of the " +
                                    "electricity, so the miner offsets roughly " +
                                    (state.valuePerMonthHeatPump?.let { money(it, state.currencyCode) } ?: "—") +
                                    "/month against one.",
                                style = MaterialTheme.typography.labelSmall,
                                color = HiBrand.textSecondary,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Off heating season the heat has no value and adds cooling load — pair this with " +
                        "Schedules to wind down in summer.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun BigCard(label: String, value: String, sub: String, accent: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (accent) HiBrand.accent.copy(alpha = 0.15f) else HiBrand.surface,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (accent) HiBrand.accent else HiBrand.textPrimary,
            )
            Text(sub, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
        }
    }
}

private fun money(value: Double, currency: String): String = "%,.2f %s".format(value, currency)
