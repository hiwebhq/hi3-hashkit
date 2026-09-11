package hi3.hashkit.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.core.Units
import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

enum class SortColumn { NAME, IP, MODEL, HASHRATE, TEMP, POOL, UPTIME, EFFICIENCY }

data class TableState(
    val miners: List<Miner> = emptyList(),
    val fahrenheit: Boolean = false,
    val query: String = "",
    val sort: SortColumn = SortColumn.NAME,
    val ascending: Boolean = true,
    val advancedUnlocked: Boolean = false,
)

@HiltViewModel
class TableViewModel @Inject constructor(
    private val repository: MinerRepository,
    private val pollingEngine: PollingEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(SortColumn.NAME)
    private val ascending = MutableStateFlow(true)

    fun setQuery(v: String) { query.value = v }
    fun toggleSort(col: SortColumn) {
        if (sort.value == col) ascending.value = !ascending.value
        else { sort.value = col; ascending.value = true }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TableState> =
        combine(
            combine(
                repository.observeMinerEntities(),
                pollingEngine.lastRefresh,
                settingsRepository.settings,
            ) { entities, _, settings ->
                val now = Instant.now()
                Triple(
                    entities.filter { settings.demoModeEnabled || !it.isDemo }.map { repository.toDomain(it, now) },
                    settings.useFahrenheit,
                    settings.advancedUnlocked,
                )
            },
            query, sort, ascending,
        ) { (miners, fahrenheit, advanced), q, s, asc ->
            val filtered = if (q.isBlank()) miners else miners.filter {
                it.name.contains(q, true) || it.host.contains(q, true) ||
                    (it.identity.model?.contains(q, true) == true) ||
                    (it.lastTelemetry?.poolUrl?.contains(q, true) == true)
            }
            TableState(sortMiners(filtered, s, asc), fahrenheit, q, s, asc, advancedUnlocked = advanced)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TableState())

    companion object {
        fun sortMiners(miners: List<Miner>, sort: SortColumn, ascending: Boolean): List<Miner> {
            val cmp: Comparator<Miner> = when (sort) {
                SortColumn.NAME -> compareBy { it.name.lowercase() }
                SortColumn.IP -> compareBy { ipKey(it.host) }
                SortColumn.MODEL -> compareBy { (it.identity.model ?: "").lowercase() }
                SortColumn.HASHRATE -> compareBy { it.lastTelemetry?.hashrateGhs?.value ?: -1.0 }
                SortColumn.TEMP -> compareBy { it.lastTelemetry?.chipTempC?.value ?: -1.0 }
                SortColumn.POOL -> compareBy { (it.lastTelemetry?.poolUrl ?: "").lowercase() }
                SortColumn.UPTIME -> compareBy { it.lastTelemetry?.uptimeSeconds ?: -1L }
                SortColumn.EFFICIENCY -> compareBy { it.lastTelemetry?.efficiencyJTh?.value ?: Double.MAX_VALUE }
            }
            return miners.sortedWith(if (ascending) cmp else cmp.reversed())
        }

        /** Sort IPv4 addresses numerically by octet, not lexicographically. */
        private fun ipKey(host: String): Long {
            val parts = host.substringBefore(':').split(".").mapNotNull { it.toIntOrNull() }
            if (parts.size != 4) return Long.MAX_VALUE
            return parts.fold(0L) { acc, o -> acc * 256 + o }
        }
    }
}

private data class Col(val title: String, val sort: SortColumn, val width: Int)

private val COLUMNS = listOf(
    Col("Name", SortColumn.NAME, 130),
    Col("IP", SortColumn.IP, 110),
    Col("Model", SortColumn.MODEL, 120),
    Col("Hashrate", SortColumn.HASHRATE, 100),
    Col("Temp", SortColumn.TEMP, 74),
    Col("Fan", SortColumn.NAME, 74),
    Col("Pool", SortColumn.POOL, 150),
    Col("Uptime", SortColumn.UPTIME, 92),
    Col("J/TH", SortColumn.EFFICIENCY, 78),
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TableScreen(
    onBack: () -> Unit,
    onMinerClick: (Long) -> Unit,
    onProgramNfc: (List<Long>) -> Unit = {},
    onScan: () -> Unit = {},
    viewModel: TableViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hScroll = rememberScrollState()
    val totalWidth = COLUMNS.sumOf { it.width }.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fleet table", fontWeight = FontWeight.Bold) },
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
                label = { Text("Filter (name, IP, model, pool)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        hi3.hashkit.integrations.print.AssetTagPrinter.print(
                            context,
                            state.miners.map {
                                hi3.hashkit.integrations.print.AssetTagPrinter.TagData(
                                    name = it.name,
                                    mac = it.identity.macAddress,
                                    ip = it.host,
                                    location = it.location,
                                )
                            },
                        )
                    },
                    enabled = state.miners.isNotEmpty(),
                ) { Text("Print QR codes") }
                if (state.advancedUnlocked) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onProgramNfc(state.miners.map { it.id }) },
                        enabled = state.miners.isNotEmpty(),
                    ) { Text("Program NFC tags") }
                    androidx.compose.material3.OutlinedButton(
                        onClick = onScan,
                    ) { Text("Scan tag / QR") }
                }
            }
            Text(
                "${state.miners.size} tag(s) · scannable in the AR rack overlay",
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Column(Modifier.horizontalScroll(hScroll)) {
                // Header
                Row(
                    Modifier.width(totalWidth)
                        .background(HiBrand.surfaceRaised)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    COLUMNS.forEach { col ->
                        val active = state.sort == col.sort && col.title != "Fan"
                        val arrow = if (active) (if (state.ascending) " ▲" else " ▼") else ""
                        Text(
                            col.title + arrow,
                            modifier = Modifier
                                .width(col.width.dp)
                                .clickable(enabled = col.title != "Fan") { viewModel.toggleSort(col.sort) }
                                .padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) HiBrand.accent else HiBrand.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.miners, key = { it.id }) { m ->
                        TableRow(m, state.fahrenheit, totalWidth) { onMinerClick(m.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRow(miner: Miner, fahrenheit: Boolean, totalWidth: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val t = miner.lastTelemetry
    val statusColor = when (miner.status) {
        MinerStatus.ONLINE -> HiBrand.statusOnline
        MinerStatus.DEGRADED -> HiBrand.statusDegraded
        MinerStatus.OFFLINE -> HiBrand.statusOffline
        MinerStatus.UNKNOWN -> HiBrand.statusUnknown
    }
    val fan = t?.fans?.firstOrNull()?.let { it.rpm?.let { r -> "$r" } ?: it.percent?.let { p -> "$p%" } } ?: "—"
    val cells = listOf(
        miner.name,
        miner.host,
        miner.identity.model ?: "—",
        if (miner.status == MinerStatus.OFFLINE) "offline" else Units.formatHashrate(t?.hashrateGhs?.value),
        Units.formatTemp(t?.chipTempC?.value, fahrenheit),
        fan,
        t?.poolUrl?.substringAfter("//")?.ifBlank { "—" } ?: "—",
        Units.formatUptime(t?.uptimeSeconds),
        t?.efficiencyJTh?.value?.let { "%.1f".format(it) } ?: "—",
    )
    Row(
        Modifier.width(totalWidth).clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { i, value ->
            Row(
                Modifier.width(COLUMNS[i].width.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (i == 0) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(9.dp).clip(CircleShape).background(statusColor),
                    )
                }
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}
