package hi3.hashkit.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
                title = { Text(HiBrand.appName, fontWeight = FontWeight.Bold) },
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
            item { FleetSummary(state) }
            state.solo?.let { solo -> item { SoloCard(solo) } }
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
                    items(groupMiners, key = { it.id }) { miner ->
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

@Composable
private fun BulkBar(
    count: Int,
    onReboot: () -> Unit,
    onPool: () -> Unit,
    onFan: () -> Unit,
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = onReboot) { Text("Restart") }
                androidx.compose.material3.OutlinedButton(onClick = onPool) { Text("Pool") }
                androidx.compose.material3.OutlinedButton(onClick = onFan) { Text("Fan") }
                androidx.compose.material3.TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun FleetSummary(state: DashboardUiState) {
    val totals = state.totals
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("FLEET", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            Spacer(Modifier.height(6.dp))
            Text(
                Units.formatHashrate(totals?.totalHashrateGhs),
                style = MaterialTheme.typography.headlineLarge,
                color = HiBrand.accent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric("Power", Units.formatPower(totals?.totalMeasuredPowerW))
                Metric("Efficiency", Units.formatEfficiency(totals?.fleetEfficiencyJTh))
                Metric("Hottest", Units.formatTemp(totals?.hottestChipC, state.settings.useFahrenheit))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Counter(totals?.online ?: 0, "online", HiBrand.statusOnline)
                Counter(totals?.degraded ?: 0, "degraded", HiBrand.statusDegraded)
                Counter(totals?.offline ?: 0, "offline", HiBrand.statusOffline)
                Counter(totals?.unknown ?: 0, "stale", HiBrand.statusUnknown)
            }
            totals?.dailyCost?.let { cost ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "Est. electricity: ${"%.2f".format(cost)} ${totals.currencyCode}/day " +
                        "(from measured power and your configured rate)",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                lastRefreshLabel(state.lastRefresh),
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
        }
    }
}

@Composable
private fun SoloCard(solo: hi3.hashkit.ui.dashboard.SoloSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(16.dp),
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
    if (instant == null) return "No successful refresh yet"
    val secs = Duration.between(instant, Instant.now()).seconds
    return when {
        secs < 5 -> "Refreshed just now"
        secs < 120 -> "Refreshed ${secs}s ago"
        else -> "Refreshed ${secs / 60}m ago — data may be stale"
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
                        listOfNotNull(miner.identity.model, miner.host).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                }
                StatusBadge(miner.status)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric(
                    "Hashrate",
                    Units.formatHashrate(t?.hashrateGhs?.value),
                    valueColor = HiBrand.accent,
                )
                Metric("Power", Units.formatPower(t?.powerW?.value), source = t?.powerW?.source)
                Metric("Chip", Units.formatTemp(t?.chipTempC?.value))
                Metric("Eff.", Units.formatEfficiency(t?.efficiencyJTh?.value), source = t?.efficiencyJTh?.source)
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
