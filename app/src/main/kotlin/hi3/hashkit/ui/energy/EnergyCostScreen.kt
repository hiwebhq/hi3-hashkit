package hi3.hashkit.ui.energy

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.R
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

private const val DAYS_PER_MONTH = hi3.hashkit.domain.heat.HeatReuseMath.DAYS_PER_MONTH

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
    val efficiencyJTh: Double?,
    val revenuePerDay: Double?,
    val netPerDay: Double?,
    val online: Boolean,
)

enum class EnergySort(@StringRes val labelRes: Int) {
    COST(R.string.energy_sort_cost),
    EFFICIENCY(R.string.energy_sort_efficiency),
    NET(R.string.energy_sort_net),
}

data class EnergyCostState(
    val rows: List<EnergyRow> = emptyList(),
    val query: String = "",
    val sort: EnergySort = EnergySort.COST,
    val currency: String = "USD",
    val ratePerKwh: Double = 0.0,
    val totalCostDay: Double? = null,
    val totalKwhDay: Double = 0.0,
    val totalRevenueDay: Double? = null,
    val totalNetDay: Double? = null,
    /** True when difficulty + BTC price are available, i.e. revenue columns mean something. */
    val revenueAvailable: Boolean = false,
)

@HiltViewModel
class EnergyCostViewModel @Inject constructor(
    private val repository: MinerRepository,
    private val pollingEngine: PollingEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    fun setQuery(v: String) { query.value = v }

    private val sort = MutableStateFlow(EnergySort.COST)
    fun setSort(v: EnergySort) { sort.value = v }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<EnergyCostState> =
        combine(
            combine(repository.observeMinerEntities(), pollingEngine.lastRefresh, settingsRepository.settings) { entities, _, settings ->
                val now = Instant.now()
                val rate = settings.electricityRatePerKwh
                val miners = entities
                    .filter { settings.demoModeEnabled || !it.isDemo }
                    .map { repository.toDomain(it, now) }
                // Same difficulty/price resolution as the dashboard profit card: the freshest
                // miner-reported difficulty, else the (opt-in fetched or manual) setting.
                val difficulty = miners.mapNotNull { it.lastTelemetry?.networkDifficulty }.maxOrNull()
                    ?: settings.networkDifficulty.takeIf { it > 0 }
                val price = settings.btcPrice.takeIf { it > 0 }
                val rows = miners.map { m ->
                    val power = m.lastTelemetry?.powerW?.value
                    val hashrate = m.lastTelemetry?.hashrateGhs?.value
                    val cost = ProfitMath.powerCostPerDay(power, rate.takeIf { it > 0 })
                    val revenue = ProfitMath.revenuePerDay(ProfitMath.btcPerDay(hashrate, difficulty), price)
                    EnergyRow(
                        id = m.id,
                        name = m.name,
                        host = m.host,
                        model = m.identity.model,
                        powerW = power,
                        estimated = m.lastTelemetry?.powerW?.source == ValueSource.ESTIMATED || power == null,
                        kwhPerDay = ProfitMath.energyKwhPerDay(power),
                        costPerDay = cost,
                        costPerMonth = cost?.times(DAYS_PER_MONTH),
                        efficiencyJTh = m.lastTelemetry?.efficiencyJTh?.value
                            ?: hi3.hashkit.core.Units.efficiencyJTh(power, hashrate),
                        revenuePerDay = revenue,
                        netPerDay = ProfitMath.netPerDay(revenue, cost),
                        online = m.status == MinerStatus.ONLINE || m.status == MinerStatus.DEGRADED,
                    )
                }
                RowsBundle(rows, settings.currencyCode, rate, revenueAvailable = difficulty != null && price != null)
            },
            query,
            sort,
        ) { bundle, q, s ->
            val filtered = if (q.isBlank()) bundle.rows else bundle.rows.filter {
                it.name.contains(q, true) || it.host.contains(q, true) || (it.model?.contains(q, true) == true)
            }
            EnergyCostState(
                rows = sortRows(filtered, s),
                query = q,
                sort = s,
                currency = bundle.currency,
                ratePerKwh = bundle.rate,
                totalCostDay = filtered.mapNotNull { it.costPerDay }.takeIf { it.isNotEmpty() }?.sum(),
                totalKwhDay = filtered.mapNotNull { it.kwhPerDay }.sum(),
                totalRevenueDay = filtered.mapNotNull { it.revenuePerDay }.takeIf { it.isNotEmpty() }?.sum(),
                totalNetDay = filtered.mapNotNull { it.netPerDay }.takeIf { it.isNotEmpty() }?.sum(),
                revenueAvailable = bundle.revenueAvailable,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnergyCostState())

    private data class RowsBundle(
        val rows: List<EnergyRow>,
        val currency: String,
        val rate: Double,
        val revenueAvailable: Boolean,
    )

    companion object {
        /** League-table ordering: cost + net descending (big first), efficiency ascending (lower J/TH wins). */
        fun sortRows(rows: List<EnergyRow>, sort: EnergySort): List<EnergyRow> = when (sort) {
            EnergySort.COST -> rows.sortedByDescending { it.costPerDay ?: it.powerW ?: 0.0 }
            EnergySort.EFFICIENCY -> rows.sortedBy { it.efficiencyJTh ?: Double.MAX_VALUE }
            EnergySort.NET -> rows.sortedByDescending { it.netPerDay ?: -Double.MAX_VALUE }
        }
    }
}

@Suppress("LongMethod") // a declarative screen: filter + totals + row list
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
                title = { Text(stringResource(R.string.energy_title), fontWeight = FontWeight.Bold) },
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.energy_filter_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Row(
                Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                EnergySort.entries.forEach { s ->
                    FilterChip(
                        selected = state.sort == s,
                        onClick = { viewModel.setSort(s) },
                        label = { Text(stringResource(s.labelRes)) },
                    )
                }
            }
            if (state.ratePerKwh <= 0.0) {
                Text(
                    stringResource(R.string.energy_rate_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (!state.revenueAvailable) {
                Text(
                    stringResource(R.string.energy_revenue_hint),
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
                    Metric(
                        stringResource(R.string.energy_fleet_energy_est),
                        state.totalCostDay?.let { stringResource(R.string.energy_per_day_fmt, money(it, state.currency)) } ?: "—",
                        valueColor = HiBrand.accent,
                    )
                    Metric(
                        stringResource(R.string.energy_revenue_est),
                        state.totalRevenueDay?.let { stringResource(R.string.energy_per_day_fmt, money(it, state.currency)) } ?: "—",
                        source = state.totalRevenueDay?.let { ValueSource.ESTIMATED },
                    )
                    Metric(
                        stringResource(R.string.energy_net_est),
                        state.totalNetDay?.let { stringResource(R.string.energy_per_day_fmt, money(it, state.currency)) } ?: "—",
                        valueColor = netColor(state.totalNetDay),
                    )
                    Metric(stringResource(R.string.energy_this_month), state.totalCostDay?.let { money(it * DAYS_PER_MONTH, state.currency) } ?: "—")
                    Metric(stringResource(R.string.energy_energy), "%,.1f kWh/day".format(state.totalKwhDay))
                    Metric(stringResource(R.string.energy_machines), "${state.rows.size}")
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                    EnergyRowCard(row, rank = index + 1, currency = state.currency) { onMinerClick(row.id) }
                }
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod") // one branch per optional metric on the card
@Composable
private fun EnergyRowCard(row: EnergyRow, rank: Int, currency: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#$rank  ${row.name}", style = MaterialTheme.typography.titleSmall, color = HiBrand.textPrimary)
                Text(
                    if (!row.online) stringResource(R.string.energy_offline) else row.host,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!row.online) HiBrand.statusOffline else HiBrand.textSecondary,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Metric(
                    stringResource(R.string.energy_power),
                    row.powerW?.let { "%.0f W".format(it) } ?: "—",
                    source = if (row.estimated) ValueSource.ESTIMATED else null,
                )
                Metric(stringResource(R.string.energy_efficiency), row.efficiencyJTh?.let { hi3.hashkit.core.Units.formatEfficiency(it) } ?: "—")
                Metric(
                    stringResource(R.string.energy_cost),
                    row.costPerDay?.let { stringResource(R.string.energy_per_day_fmt, money(it, currency)) } ?: "—",
                    valueColor = HiBrand.accent,
                )
                Metric(
                    stringResource(R.string.energy_revenue),
                    row.revenuePerDay?.let { stringResource(R.string.energy_per_day_fmt, money(it, currency)) } ?: "—",
                    source = row.revenuePerDay?.let { ValueSource.ESTIMATED },
                )
                Metric(
                    stringResource(R.string.energy_net),
                    row.netPerDay?.let { stringResource(R.string.energy_per_day_fmt, money(it, currency)) } ?: "—",
                    valueColor = netColor(row.netPerDay),
                )
                Metric(stringResource(R.string.energy_energy), row.kwhPerDay?.let { "%.2f kWh/day".format(it) } ?: "—")
                Metric(stringResource(R.string.energy_est_month), row.costPerMonth?.let { money(it, currency) } ?: "—")
            }
        }
    }
}

@Composable
private fun netColor(net: Double?) = when {
    net == null -> HiBrand.textPrimary
    net >= 0 -> HiBrand.statusOnline
    else -> HiBrand.statusOffline
}

private fun money(value: Double, currency: String): String =
    hi3.hashkit.core.Units.formatMoney(value, currency)
