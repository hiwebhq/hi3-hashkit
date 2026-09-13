package hi3.hashkit.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

enum class SortColumn { NAME, IP, MODEL, HASHRATE, TEMP, POOL, UPTIME, EFFICIENCY, FREQUENCY, VOLTAGE }

data class TableState(
    val miners: List<Miner> = emptyList(),
    val fahrenheit: Boolean = false,
    val query: String = "",
    val sort: SortColumn = SortColumn.NAME,
    val ascending: Boolean = true,
    val advancedUnlocked: Boolean = false,
    val inventoryTagType: hi3.hashkit.data.prefs.InventoryTagType = hi3.hashkit.data.prefs.InventoryTagType.BOTH,
    /** Long-press a row to start selecting; selection enables assign-to-farm and delete. */
    val selection: Set<Long> = emptySet(),
    val farms: List<hi3.hashkit.data.db.FarmEntity> = emptyList(),
)

private data class TableSettingsBundle(
    val miners: List<Miner>,
    val fahrenheit: Boolean,
    val advancedUnlocked: Boolean,
    val inventoryTagType: hi3.hashkit.data.prefs.InventoryTagType,
)

@HiltViewModel
class TableViewModel @Inject constructor(
    private val repository: MinerRepository,
    private val pollingEngine: PollingEngine,
    private val settingsRepository: SettingsRepository,
    private val nfcRouter: hi3.hashkit.data.nfc.NfcRouter,
    private val farmRepository: hi3.hashkit.data.repo.FarmRepository,
) : ViewModel() {

    /** (miner id, nonce) to highlight after a scan; nonce lets the same miner re-highlight. */
    val highlight: StateFlow<Pair<Long, Long>?> = nfcRouter.highlight
    fun clearHighlight() = nfcRouter.clearHighlight()

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(SortColumn.NAME)
    private val ascending = MutableStateFlow(true)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    fun setQuery(v: String) { query.value = v }
    fun toggleSort(col: SortColumn) {
        if (sort.value == col) ascending.value = !ascending.value
        else { sort.value = col; ascending.value = true }
    }

    /** The UI computes toggles / select-all-filtered / clear and hands back the new set. */
    fun setSelection(ids: Set<Long>) { selection.value = ids }

    fun deleteSelected() {
        viewModelScope.launch {
            state.value.selection.forEach { repository.deleteMiner(it) }
            selection.value = emptySet()
        }
    }

    fun assignSelectedToFarm(farmId: Long?) {
        viewModelScope.launch {
            state.value.selection.forEach { farmRepository.assignMiner(it, farmId) }
            selection.value = emptySet()
        }
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
                TableSettingsBundle(
                    entities.filter { settings.demoModeEnabled || !it.isDemo }.map { repository.toDomain(it, now) },
                    settings.useFahrenheit,
                    settings.advancedUnlocked,
                    settings.inventoryTagType,
                )
            },
            combine(query, sort, ascending) { q, s, asc -> Triple(q, s, asc) },
            combine(selection, farmRepository.observeFarms()) { sel, farms -> sel to farms },
        ) { bundle, (q, s, asc), (sel, farms) ->
            val filtered = if (q.isBlank()) bundle.miners else bundle.miners.filter {
                it.name.contains(q, true) || it.host.contains(q, true) ||
                    (it.identity.model?.contains(q, true) == true) ||
                    (it.lastTelemetry?.poolUrl?.contains(q, true) == true)
            }
            TableState(
                sortMiners(filtered, s, asc), bundle.fahrenheit, q, s, asc,
                advancedUnlocked = bundle.advancedUnlocked, inventoryTagType = bundle.inventoryTagType,
                selection = sel.filter { id -> bundle.miners.any { it.id == id } }.toSet(),
                farms = farms,
            )
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
                SortColumn.FREQUENCY -> compareBy { it.lastTelemetry?.frequencyMhz?.value ?: -1.0 }
                SortColumn.VOLTAGE -> compareBy { it.lastTelemetry?.coreVoltageMv?.value ?: -1.0 }
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
    Col("MHz", SortColumn.FREQUENCY, 74),
    Col("mV", SortColumn.VOLTAGE, 74),
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
    var showPrintSize by remember { mutableStateOf(false) }
    var showAssignFarm by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    fun toggleSelect(id: Long) {
        val sel = state.selection
        viewModel.setSelection(if (id in sel) sel - id else sel + id)
    }
    // After a scan, scroll to and highlight the scanned miner in the list.
    val highlight by viewModel.highlight.collectAsStateWithLifecycle()
    val highlightId = highlight?.first
    val listState = rememberLazyListState()
    LaunchedEffect(highlight?.second) {
        val id = highlight?.first ?: return@LaunchedEffect
        var idx = -1
        var tries = 0
        while (idx < 0 && tries < 20) {
            idx = viewModel.state.value.miners.indexOfFirst { it.id == id }
            if (idx < 0) { kotlinx.coroutines.delay(100); tries++ }
        }
        if (idx >= 0) runCatching { listState.animateScrollToItem(idx) }
        kotlinx.coroutines.delay(4000)
        viewModel.clearHighlight()
    }
    val totalWidth = COLUMNS.sumOf { it.width }.dp
    // Landscape: drop all chrome so the machines table itself gets the whole screen
    // (the system back gesture still exits; rotate back for search/print/scan tools).
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            if (!landscape) {
                TopAppBar(
                    title = { Text("Fleet table", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
                )
            }
        },
        containerColor = HiBrand.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (!landscape) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Filter (name, IP, model, pool)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) {
                    if (state.inventoryTagType.showQr) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { showPrintSize = true },
                            enabled = state.miners.isNotEmpty(),
                        ) { Text("Print QR codes") }
                    }
                    if (state.advancedUnlocked) {
                        if (state.inventoryTagType.showNfc) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { onProgramNfc(state.miners.map { it.id }) },
                                enabled = state.miners.isNotEmpty(),
                            ) { Text("Program NFC tags") }
                        }
                        androidx.compose.material3.OutlinedButton(
                            onClick = onScan,
                        ) { Text(if (state.inventoryTagType.showQr) "Scan tag / QR" else "Scan NFC") }
                    }
                }
                Text(
                    "${state.miners.size} tag(s) · scannable in the AR rack overlay · long-press a row to select",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (state.selection.isNotEmpty()) {
                TableSelectionBar(
                    count = state.selection.size,
                    filteredCount = state.miners.size,
                    onSelectAll = { viewModel.setSelection(state.miners.map { it.id }.toSet()) },
                    onAssignFarm = { showAssignFarm = true },
                    onDelete = { confirmDeleteSelected = true },
                    onClear = { viewModel.setSelection(emptySet()) },
                )
            }
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
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.miners, key = { it.id }) { m ->
                        TableRow(
                            m, state.fahrenheit, totalWidth,
                            highlighted = m.id == highlightId,
                            selected = m.id in state.selection,
                            onLongClick = { toggleSelect(m.id) },
                        ) {
                            if (state.selection.isNotEmpty()) toggleSelect(m.id) else onMinerClick(m.id)
                        }
                    }
                }
            }

            if (showAssignFarm) {
                AssignFarmDialog(
                    farms = state.farms,
                    count = state.selection.size,
                    onPick = { farmId -> showAssignFarm = false; viewModel.assignSelectedToFarm(farmId) },
                    onDismiss = { showAssignFarm = false },
                )
            }
            if (confirmDeleteSelected) {
                ConfirmDeleteSelectedDialog(
                    count = state.selection.size,
                    onConfirm = { confirmDeleteSelected = false; viewModel.deleteSelected() },
                    onDismiss = { confirmDeleteSelected = false },
                )
            }
            if (showPrintSize) {
                PrintSizeDialog(
                    onDismiss = { showPrintSize = false },
                    onPick = { size ->
                        showPrintSize = false
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
                            size,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PrintSizeDialog(
    onDismiss: () -> Unit,
    onPick: (hi3.hashkit.integrations.print.AssetTagPrinter.TagSize) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("QR sticker size") },
        text = {
            Column {
                Text(
                    "How big should each printed sticker be? Smaller fits more per page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
                hi3.hashkit.integrations.print.AssetTagPrinter.TagSize.entries.forEach { size ->
                    androidx.compose.material3.TextButton(onClick = { onPick(size) }) { Text(size.label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Suppress("CyclomaticComplexMethod") // flat per-column formatting: one small branch per cell
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TableRow(
    miner: Miner,
    fahrenheit: Boolean,
    totalWidth: androidx.compose.ui.unit.Dp,
    highlighted: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
) {
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
        // Not all firmwares report tune values (Avalon/Antminer-class don't) — show "—".
        t?.frequencyMhz?.value?.let { "%.0f".format(it) } ?: "—",
        t?.coreVoltageMv?.value?.let { "%.0f".format(it) } ?: "—",
    )
    Row(
        Modifier.width(totalWidth)
            .then(
                when {
                    highlighted ->
                        Modifier.background(HiBrand.accent.copy(alpha = 0.22f))
                            .border(2.dp, HiBrand.accent, RoundedCornerShape(8.dp))
                    selected -> Modifier.background(HiBrand.accent.copy(alpha = 0.12f))
                    else -> Modifier
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
