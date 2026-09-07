package hi3.hashkit.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Refresh
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
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
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
            if (state.miners.isEmpty()) {
                item { EmptyState(onAddMiner, onEnableDemo = { viewModel.setDemoMode(true) }) }
            } else {
                items(state.miners, key = { it.id }) { miner ->
                    MinerCard(miner, onClick = { onMinerClick(miner.id) })
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
private fun MinerCard(miner: Miner, onClick: () -> Unit) {
    val t = miner.lastTelemetry
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(14.dp),
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
