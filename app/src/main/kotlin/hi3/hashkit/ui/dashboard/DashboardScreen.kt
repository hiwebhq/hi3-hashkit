package hi3.hashkit.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.core.Units
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.ui.components.Metric
import hi3.hashkit.ui.components.StatusBadge
import hi3.hashkit.ui.theme.HiBrand
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onMinerClick: (Long) -> Unit,
    onAddMiner: () -> Unit,
    onAlerts: () -> Unit,
    onSettings: () -> Unit,
    onSchedules: () -> Unit,
    onFlow: () -> Unit,
    onExit: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var bulkKind by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<BulkActionKind?>(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { hi3.hashkit.ui.theme.HiLogo() },
                actions = {
                    IconButton(onClick = onAlerts) {
                        BadgedBox(
                            badge = {
                                if (state.unresolvedAlerts > 0) {
                                    Badge { Text("${state.unresolvedAlerts}") }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Alerts")
                        }
                    }
                    IconButton(onClick = onFlow) {
                        Icon(Icons.Filled.AccountTree, contentDescription = "Flow view")
                    }
                    IconButton(onClick = { viewModel.refreshNow() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onSchedules) {
                        Icon(Icons.Filled.Schedule, contentDescription = "Schedules")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onExit) {
                        Icon(
                            Icons.Filled.PowerSettingsNew,
                            contentDescription = "Exit app",
                            tint = HiBrand.statusOffline,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HiBrand.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMiner) {
                Icon(Icons.Filled.Add, contentDescription = "Add miner")
            }
        },
        containerColor = HiBrand.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val trend by viewModel.fleetTrend.collectAsStateWithLifecycle()
                val window by viewModel.fleetWindowMs.collectAsStateWithLifecycle()
                FleetSummary(state, trend, window, viewModel::setFleetWindow)
            }
            if (state.settings.showSoloCard) {
                state.solo?.let { solo -> item { SoloCard(solo) } }
            }
            item {
                val poolState by viewModel.poolState.collectAsStateWithLifecycle()
                if (poolState.enabled) Hi3PoolCard(poolState)
            }
            item {
                val mmpState by viewModel.mmpState.collectAsStateWithLifecycle()
                if (mmpState.enabled) MmpCard(mmpState, state)
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearch,
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    DensitySelector(
                        current = state.settings.cardDensity,
                        onSelect = viewModel::setDensity,
                    )
                }
            }
            if (state.miners.isEmpty() && state.searchQuery.isBlank()) {
                item { EmptyState(onAddMiner, onEnableDemo = { viewModel.setDemoMode(true) }) }
            } else {
                state.groups.forEach { (group, groupMiners) ->
                    if (state.groups.size > 1 || group != null) {
                        item(key = "group-${group ?: "~none"}") {
                            Text(
                                (group ?: "Ungrouped").uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = HiBrand.textSecondary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    if (state.settings.cardDensity == hi3.hashkit.data.prefs.CardDensity.GRID) {
                        items(groupMiners.chunked(2), key = { it.first().id }) { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { miner ->
                                    Box(Modifier.weight(1f)) {
                                        MinerCard(
                                            miner = miner,
                                            density = state.settings.cardDensity,
                                            selected = miner.id in state.selection,
                                            selectionMode = state.selection.isNotEmpty(),
                                            onClick = {
                                                if (state.selection.isNotEmpty()) viewModel.toggleSelect(miner.id)
                                                else onMinerClick(miner.id)
                                            },
                                            onLongClick = { viewModel.toggleSelect(miner.id) },
                                        )
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else items(groupMiners, key = { it.id }) { miner ->
                        MinerCard(
                            miner = miner,
                            density = state.settings.cardDensity,
                            selected = miner.id in state.selection,
                            selectionMode = state.selection.isNotEmpty(),
                            onClick = {
                                if (state.selection.isNotEmpty()) viewModel.toggleSelect(miner.id)
                                else onMinerClick(miner.id)
                            },
                            onLongClick = { viewModel.toggleSelect(miner.id) },
                        )
                    }
                }
                if (state.selection.isNotEmpty()) {
                    item {
                        BulkBar(
                            count = state.selection.size,
                            onReboot = { bulkKind = BulkActionKind.REBOOT },
                            onPool = { bulkKind = BulkActionKind.POOL },
                            onFan = { bulkKind = BulkActionKind.FAN },
                            onPause = { bulkKind = BulkActionKind.PAUSE },
                            onResume = { bulkKind = BulkActionKind.RESUME },
                            onClear = viewModel::clearSelection,
                        )
                    }
                }
                if (state.settings.demoModeEnabled) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Demo mode is showing synthetic miners",
                                style = MaterialTheme.typography.bodySmall,
                                color = HiBrand.statusDegraded,
                            )
                            Switch(
                                checked = true,
                                onCheckedChange = { viewModel.setDemoMode(false) },
                            )
                        }
                    }
                }
            }
        }
    }

    BulkDialogs(
        state = state,
        bulkKind = bulkKind,
        onDismissKind = { bulkKind = null },
        viewModel = viewModel,
    )
}

@Composable
private fun BulkDialogs(
    state: DashboardUiState,
    bulkKind: BulkActionKind?,
    onDismissKind: () -> Unit,
    viewModel: DashboardViewModel,
) {
    val bulk = state.bulk
    when {
        bulk.outcomes != null -> BulkResultsDialog(
            outcomes = bulk.outcomes,
            skippedCount = bulk.plan?.skipped?.size ?: 0,
            onDismiss = { viewModel.clearSelection() },
        )
        bulk.plan != null -> BulkPlanDialog(
            plan = bulk.plan,
            running = bulk.running,
            onExecute = viewModel::executeBulk,
            onDismiss = viewModel::dismissBulk,
        )
        bulkKind != null -> BulkParamsDialog(
            kind = bulkKind,
            onPlan = { action ->
                onDismissKind()
                viewModel.planBulk(action)
            },
            onDismiss = onDismissKind,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BulkBar(
    count: Int,
    onReboot: () -> Unit,
    onPool: () -> Unit,
    onFan: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surfaceRaised),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "$count selected — bulk actions run only on devices that support them",
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                androidx.compose.material3.OutlinedButton(onClick = onReboot) { Text("Restart") }
                androidx.compose.material3.OutlinedButton(onClick = onPool) { Text("Pool") }
                androidx.compose.material3.OutlinedButton(onClick = onFan) { Text("Fan") }
                androidx.compose.material3.OutlinedButton(onClick = onPause) { Text("Pause") }
                androidx.compose.material3.OutlinedButton(onClick = onResume) { Text("Resume") }
                androidx.compose.material3.TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FleetSummary(
    state: DashboardUiState,
    trend: List<hi3.hashkit.data.repo.FleetTrendPoint>,
    windowMs: Long,
    onWindow: (Long) -> Unit,
) {
    val totals = state.totals
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("FLEET", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                Text(
                    lastRefreshLabel(state.lastRefresh),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    Units.formatHashrate(totals?.totalHashrateGhs),
                    style = MaterialTheme.typography.headlineMedium,
                    color = HiBrand.accent,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Counter(totals?.online ?: 0, "on", HiBrand.statusOnline)
                    (totals?.degraded ?: 0).takeIf { it > 0 }?.let { Counter(it, "deg", HiBrand.statusDegraded) }
                    (totals?.offline ?: 0).takeIf { it > 0 }?.let { Counter(it, "off", HiBrand.statusOffline) }
                    (totals?.unknown ?: 0).takeIf { it > 0 }?.let { Counter(it, "stale", HiBrand.statusUnknown) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric("Power", Units.formatPower(totals?.totalMeasuredPowerW))
                Metric("Efficiency", Units.formatEfficiency(totals?.fleetEfficiencyJTh))
                Metric("Hottest", Units.formatTemp(totals?.hottestChipC, state.settings.useFahrenheit))
                totals?.dailyCost?.let { cost ->
                    Metric("Est. cost", "%.2f %s/d".format(cost, totals.currencyCode))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Fleet hashrate",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1h" to 3_600_000L, "6h" to 21_600_000L, "24h" to 86_400_000L).forEach { (label, ms) ->
                        FilterChip(
                            selected = windowMs == ms,
                            onClick = { onWindow(ms) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (trend.size >= 2) {
                FleetTrendChart(trend, Modifier.fillMaxWidth().height(72.dp))
            } else {
                Text(
                    "Collecting data…",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                    modifier = Modifier.height(72.dp),
                )
            }
        }
    }
}

@Composable
private fun FleetTrendChart(
    trend: List<hi3.hashkit.data.repo.FleetTrendPoint>,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier) {
        val minT = trend.first().timeMs
        val maxT = trend.last().timeMs
        val spanT = (maxT - minT).coerceAtLeast(1)
        val maxV = (trend.maxOf { it.totalGhs } * 1.1).coerceAtLeast(1.0)
        fun x(t: Long) = (t - minT).toFloat() / spanT * size.width
        fun y(v: Double) = size.height - (v / maxV).toFloat() * size.height

        val line = androidx.compose.ui.graphics.Path()
        val area = androidx.compose.ui.graphics.Path()
        trend.forEachIndexed { i, p ->
            val px = x(p.timeMs); val py = y(p.totalGhs)
            if (i == 0) { line.moveTo(px, py); area.moveTo(px, size.height); area.lineTo(px, py) }
            else { line.lineTo(px, py); area.lineTo(px, py) }
        }
        area.lineTo(x(maxT), size.height)
        area.close()
        drawPath(area, HiBrand.accent.copy(alpha = 0.15f))
        drawPath(
            line,
            color = HiBrand.accent,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}

@Composable
private fun MmpCard(mmp: hi3.hashkit.integrations.hi3.MmpState, dash: DashboardUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("HI3 MMP FLEET", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                Text(
                    mmp.lastUpdated?.let { "updated ${java.time.Duration.between(it, Instant.now()).seconds}s ago" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            mmp.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.statusDegraded)
                return@Column
            }
            val s = mmp.summary ?: return@Column
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric(
                    "MMP hashrate",
                    Units.formatHashrate(s.hashrateThs?.times(1000.0)),
                    valueColor = HiBrand.accentAlt,
                )
                Metric("Online", "${s.online ?: "—"}/${s.installed ?: "—"}")
                Metric("Power", s.powerKw?.let { Units.formatPower(it * 1000.0) } ?: "—")
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                s.realizationPct?.let { Metric("Realization", "${it.toInt()}%") }
                s.needsAttention?.takeIf { it > 0 }?.let {
                    Metric("Attention", "$it", valueColor = HiBrand.statusDegraded)
                }
                s.zeroHash?.takeIf { it > 0 }?.let {
                    Metric("Zero-hash", "$it", valueColor = HiBrand.statusOffline)
                }
            }
            if (mmp.sites.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                mmp.sites.forEach { site ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(site.siteName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            "${site.online ?: "?"}/${site.installed ?: "?"} · " +
                                Units.formatHashrate(site.hashrateThs?.times(1000.0)),
                            style = MaterialTheme.typography.bodySmall,
                            color = HiBrand.textSecondary,
                        )
                    }
                }
            }
            dash.totals?.let { totals ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "This app sees ${Units.formatHashrate(totals.totalHashrateGhs)} locally; " +
                        "MMP reports ${Units.formatHashrate(s.hashrateThs?.times(1000.0))} fleet-wide.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Hi3PoolCard(pool: hi3.hashkit.integrations.hi3.Hi3PoolState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("HI3 POOL", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                Text(
                    pool.lastUpdated?.let { "pool view · updated ${java.time.Duration.between(it, Instant.now()).seconds}s ago" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            pool.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.statusDegraded)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric("Pool-side hashrate", Units.formatHashrate(pool.totalPoolHashrateGhs), valueColor = HiBrand.accentAlt)
                Metric("Workers", "${pool.workersCount}")
                pool.blockHeight?.let { Metric("Height", "$it") }
            }
            if (pool.aggregatedViaProxy) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Miners reach the pool through your stratum proxy, so the pool reports " +
                        "them as ${pool.workersCount} aggregated worker(s). Comparing fleet " +
                        "total vs pool total below.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
            if (pool.comparisons.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (pool.aggregatedViaProxy) "FLEET vs POOL"
                    else "MINER vs POOL (pool averages lag live readings)",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                Spacer(Modifier.height(4.dp))
                pool.comparisons.forEach { c ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(
                            c.localMinerName ?: "${c.poolWorkerName} (no local match)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${Units.formatHashrate(c.localHashrateGhs)} → ${Units.formatHashrate(c.poolHashrateGhs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = HiBrand.textSecondary,
                        )
                        c.deltaPercent?.let { delta ->
                            Text(
                                String.format(java.util.Locale.US, " %+.0f%%", delta),
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    delta < -25 -> HiBrand.statusOffline
                                    delta < -10 -> HiBrand.statusDegraded
                                    else -> HiBrand.statusOnline
                                },
                            )
                        }
                    }
                }
            }
            if (pool.unmatchedLocal.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Not seen by this pool: ${pool.unmatchedLocal.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.statusDegraded,
                )
            }
        }
    }
}

@Composable
private fun SoloCard(solo: hi3.hashkit.ui.dashboard.SoloSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SOLO ODDS", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric("Day", hi3.hashkit.domain.solo.SoloMiningMath.formatProbability(solo.pDay))
                Metric("Week", hi3.hashkit.domain.solo.SoloMiningMath.formatProbability(solo.pWeek))
                Metric("Month", hi3.hashkit.domain.solo.SoloMiningMath.formatProbability(solo.pMonth))
                Metric("Year", hi3.hashkit.domain.solo.SoloMiningMath.formatProbability(solo.pYear))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Expected time to a block: " +
                    hi3.hashkit.domain.solo.SoloMiningMath.formatExpectedTime(solo.expectedSeconds) +
                    solo.bestDifficulty?.let { "  ·  Fleet best diff: ${Units.formatDifficulty(it)}" }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
            Text(
                "Statistical expectation, not a prediction — each share is an independent lottery ticket.",
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
        }
    }
}

@Composable
private fun Counter(count: Int, label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier
                .height(8.dp)
                .background(color, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp)
        )
        Text(
            "$count $label",
            style = MaterialTheme.typography.labelMedium,
            color = HiBrand.textSecondary,
        )
    }
}

private fun lastRefreshLabel(instant: Instant?): String {
    if (instant == null) return "no refresh yet"
    val secs = Duration.between(instant, Instant.now()).seconds
    return when {
        secs < 5 -> "just now"
        secs < 120 -> "${secs}s ago"
        else -> "${secs / 60}m ago — stale"
    }
}

@Composable
private fun DensitySelector(
    current: hi3.hashkit.data.prefs.CardDensity,
    onSelect: (hi3.hashkit.data.prefs.CardDensity) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(
            hi3.hashkit.data.prefs.CardDensity.LARGE to "L",
            hi3.hashkit.data.prefs.CardDensity.MEDIUM to "M",
            hi3.hashkit.data.prefs.CardDensity.COMPACT to "C",
            hi3.hashkit.data.prefs.CardDensity.GRID to "▦",
        ).forEach { (density, label) ->
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (density == current) HiBrand.accent else HiBrand.textSecondary,
                modifier = Modifier
                    .background(
                        if (density == current) HiBrand.accent.copy(alpha = 0.15f)
                        else HiBrand.surface,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(density) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MinerCard(
    miner: Miner,
    density: hi3.hashkit.data.prefs.CardDensity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = miner.lastTelemetry
    val clickMod = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    val colors = CardDefaults.cardColors(
        containerColor = if (selected) HiBrand.surfaceRaised else HiBrand.surface,
    )
    val border =
        if (selected) androidx.compose.foundation.BorderStroke(2.dp, HiBrand.accent) else null

    when (density) {
        hi3.hashkit.data.prefs.CardDensity.GRID -> {
            Card(
                colors = colors, border = border, shape = RoundedCornerShape(12.dp),
                modifier = clickMod.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        hi3.hashkit.ui.components.StatusDot(miner.status)
                        Text(
                            miner.name + if (miner.isDemo) " ᴰᴱᴹᴼ" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        Units.formatHashrate(t?.hashrateGhs?.value),
                        style = MaterialTheme.typography.titleLarge,
                        color = HiBrand.accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(
                            Units.formatTemp(t?.chipTempC?.value).takeIf { it != "—" },
                            Units.formatPower(t?.powerW?.value).takeIf { it != "—" },
                            t?.uptimeSeconds?.let { "up ${Units.formatUptime(it)}" },
                        ).joinToString(" · ").ifEmpty { "no telemetry" },
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            return
        }
        hi3.hashkit.data.prefs.CardDensity.COMPACT -> {
            Card(colors = colors, border = border, shape = RoundedCornerShape(8.dp), modifier = clickMod) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    hi3.hashkit.ui.components.StatusDot(miner.status)
                    Text(
                        miner.name + if (miner.isDemo) " ᴰᴱᴹᴼ" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Units.formatHashrate(t?.hashrateGhs?.value),
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        Units.formatTemp(t?.chipTempC?.value),
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                    Text(
                        Units.formatPower(t?.powerW?.value),
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }
            return
        }
        hi3.hashkit.data.prefs.CardDensity.MEDIUM -> {
            Card(colors = colors, border = border, shape = RoundedCornerShape(10.dp), modifier = clickMod) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        hi3.hashkit.ui.components.StatusDot(miner.status)
                        Text(
                            miner.name + if (miner.isDemo) " ᴰᴱᴹᴼ" else "",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Units.formatHashrate(t?.hashrateGhs?.value),
                            style = MaterialTheme.typography.titleSmall,
                            color = HiBrand.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(
                            Units.formatPower(t?.powerW?.value).takeIf { it != "—" },
                            Units.formatTemp(t?.chipTempC?.value).takeIf { it != "—" },
                            Units.formatEfficiency(t?.efficiencyJTh?.value).takeIf { it != "—" },
                            t?.uptimeSeconds?.let { "up ${Units.formatUptime(it)}" },
                            t?.attainmentPercent?.let { "${it.toInt()}% of exp." },
                        ).joinToString("  ·  ").ifEmpty { "no telemetry" },
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }
            return
        }
        hi3.hashkit.data.prefs.CardDensity.LARGE -> Unit // falls through to the full card below
    }

    Card(
        colors = colors,
        border = border,
        shape = RoundedCornerShape(14.dp),
        modifier = clickMod,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            miner.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (miner.isDemo) {
                            Text(
                                "DEMO",
                                style = MaterialTheme.typography.labelSmall,
                                color = HiBrand.statusDegraded,
                                modifier = Modifier
                                    .background(
                                        HiBrand.statusDegraded.copy(alpha = 0.15f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        listOfNotNull(
                            miner.identity.model?.takeIf { it != miner.name },
                            miner.host,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                }
                StatusBadge(miner.status)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                Metric(
                    "Hashrate",
                    Units.formatHashrate(t?.hashrateGhs?.value),
                    valueColor = HiBrand.accent,
                )
                Metric("Power", Units.formatPower(t?.powerW?.value), source = t?.powerW?.source)
                Metric("Chip", Units.formatTemp(t?.chipTempC?.value))
                Metric("Eff.", Units.formatEfficiency(t?.efficiencyJTh?.value), source = t?.efficiencyJTh?.source)
                Metric("Uptime", Units.formatUptime(t?.uptimeSeconds))
            }
        }
    }
}

@Composable
private fun EmptyState(onAddMiner: () -> Unit, onEnableDemo: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("No miners yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Hi3 Miner Watch is local-first: it talks only to miners on your network " +
                    "(or over your existing Tailscale VPN) and needs no account.",
                style = MaterialTheme.typography.bodyMedium,
                color = HiBrand.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    "Scan or add a miner",
                    color = HiBrand.accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onAddMiner() },
                )
                Text(
                    "Try demo mode",
                    color = HiBrand.accentAlt,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onEnableDemo() },
                )
            }
        }
    }
}
