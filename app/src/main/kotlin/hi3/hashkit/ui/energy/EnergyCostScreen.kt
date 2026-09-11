package hi3.hashkit.ui.energy

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.ValueSource
import hi3.hashkit.domain.solo.ProfitMath
import hi3.hashkit.ui.components.Metric
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

private const val DAYS_PER_MONTH = 30.4375

data class EnergyRow(
    val id: Long,
    val name: String,
    val host: String,
    val model: String?,
    val powerW: Double?,
    val estimated: Boolean,
    val kwhPerDay: Double?,
    val costPerDay: Double?,
    val costPerMonth: Double?,
    val online: Boolean,
)

data class EnergyCostState(
    val rows: List<EnergyRow> = emptyList(),
    val query: String = "",
    val currency: String = "USD",
    val ratePerKwh: Double = 0.0,
    val totalCostDay: Double? = null,
    val totalKwhDay: Double = 0.0,
)

@HiltViewModel
class EnergyCostViewModel @Inject constructor(
    private val repository: MinerRepository,
    private val pollingEngine: PollingEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    fun setQuery(v: String) { query.value = v }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<EnergyCostState> =
        combine(
            combine(repository.observeMinerEntities(), pollingEngine.lastRefresh, settingsRepository.settings) { entities, _, settings ->
                val now = Instant.now()
                val rate = settings.electricityRatePerKwh
                val rows = entities
                    .filter { settings.demoModeEnabled || !it.isDemo }
                    .map { repository.toDomain(it, now) }
                    .map { m ->
                        val power = m.lastTelemetry?.powerW?.value
                        EnergyRow(
                            id = m.id,
                            name = m.name,
                            host = m.host,
                            model = m.identity.model,
                            powerW = power,
                            estimated = m.lastTelemetry?.powerW?.source == ValueSource.ESTIMATED || power == null,
                            kwhPerDay = ProfitMath.energyKwhPerDay(power),
                            costPerDay = ProfitMath.powerCostPerDay(power, rate.takeIf { it > 0 }),
                            costPerMonth = ProfitMath.powerCostPerDay(power, rate.takeIf { it > 0 })?.times(DAYS_PER_MONTH),
                            online = m.status == MinerStatus.ONLINE || m.status == MinerStatus.DEGRADED,
                        )
                    }
                Triple(rows, settings.currencyCode, rate)
            },
            query,
        ) { (rows, currency, rate), q ->
            val filtered = if (q.isBlank()) rows else rows.filter {
                it.name.contains(q, true) || it.host.contains(q, true) || (it.model?.contains(q, true) == true)
            }.let { it }
            EnergyCostState(
                rows = filtered.sortedByDescending { it.costPerDay ?: it.powerW ?: 0.0 },
                query = q,
                currency = currency,
                ratePerKwh = rate,
                totalCostDay = filtered.mapNotNull { it.costPerDay }.takeIf { it.isNotEmpty() }?.sum(),
                totalKwhDay = filtered.mapNotNull { it.kwhPerDay }.sum(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnergyCostState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyCostScreen(
    onMinerClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: EnergyCostViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Energy cost", fontWeight = FontWeight.Bold) },
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text("Filter (name, IP, model)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (state.ratePerKwh <= 0.0) {
                Text(
                    "Set your electricity rate in Settings to see cost estimates (energy use shows regardless).",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            // Fleet total.
            Card(
                colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(Modifier.padding(14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Metric("Fleet Energy Est.", state.totalCostDay?.let { money(it, state.currency) + "/day" } ?: "—", valueColor = HiBrand.accent)
                    Metric("This month", state.totalCostDay?.let { money(it * DAYS_PER_MONTH, state.currency) } ?: "—")
                    Metric("Energy", "%,.1f kWh/day".format(state.totalKwhDay))
                    Metric("Machines", "${state.rows.size}")
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.rows, key = { it.id }) { row ->
                    EnergyRowCard(row, state.currency) { onMinerClick(row.id) }
                }
            }
        }
    }
}

@Composable
private fun EnergyRowCard(row: EnergyRow, currency: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.name, style = MaterialTheme.typography.titleSmall, color = HiBrand.textPrimary)
                Text(
                    if (!row.online) "offline" else row.host,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!row.online) HiBrand.statusOffline else HiBrand.textSecondary,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Metric("Power", row.powerW?.let { "%.0f W".format(it) } ?: "—", source = if (row.estimated) ValueSource.ESTIMATED else null)
                Metric("Energy", row.kwhPerDay?.let { "%.2f kWh/day".format(it) } ?: "—")
                Metric("Energy Est.", row.costPerDay?.let { money(it, currency) + "/day" } ?: "—", valueColor = HiBrand.accent)
                Metric("Est. month", row.costPerMonth?.let { money(it, currency) } ?: "—")
            }
        }
    }
}

private fun money(value: Double, currency: String): String = "%,.2f %s".format(value, currency)
