package hi3.hashkit.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.core.Units
import hi3.hashkit.ui.components.Metric
import hi3.hashkit.ui.components.StatusBadge
import hi3.hashkit.ui.theme.HiBrand
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinerDetailScreen(
    onBack: () -> Unit,
    viewModel: MinerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val miner = state.miner
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(miner?.name ?: "Miner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove miner")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        containerColor = HiBrand.background,
    ) { padding ->
        if (miner == null) {
            Text(
                "Miner not found",
                modifier = Modifier.padding(padding).padding(16.dp),
                color = HiBrand.textSecondary,
            )
            return@Scaffold
        }
        val t = miner.lastTelemetry
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        Units.formatHashrate(t?.hashrateGhs?.value),
                        style = MaterialTheme.typography.headlineLarge,
                        color = HiBrand.accent,
                        fontWeight = FontWeight.Bold,
                    )
                    t?.attainmentPercent?.let {
                        Text(
                            String.format(java.util.Locale.US, "%.1f%% of expected", it),
                            style = MaterialTheme.typography.bodySmall,
                            color = HiBrand.textSecondary,
                        )
                    }
                }
                StatusBadge(miner.status)
            }

            SectionCard("HASHRATE — LAST HOUR") {
                HashrateChart(state.history)
                Spacer(Modifier.height(4.dp))
                Text(
                    lastReadingLabel(t?.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }

            SectionCard("LIVE TELEMETRY") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    Metric("Power", Units.formatPower(t?.powerW?.value), source = t?.powerW?.source)
                    Metric("Efficiency", Units.formatEfficiency(t?.efficiencyJTh?.value), source = t?.efficiencyJTh?.source)
                    Metric("Chip temp", Units.formatTemp(t?.chipTempC?.value, state.settings.useFahrenheit))
                    Metric("VR temp", Units.formatTemp(t?.vrTempC?.value, state.settings.useFahrenheit))
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    Metric("Frequency", t?.frequencyMhz?.value?.let { "${it.toInt()} MHz" } ?: "—")
                    Metric("Core V", t?.coreVoltageMv?.value?.let { "${it.toInt()} mV" } ?: "—")
                    t?.fans?.forEach { fan ->
                        Metric(
                            "Fan ${fan.index + 1}",
                            fan.rpm?.let { "$it RPM" } ?: fan.percent?.let { "$it%" } ?: "—",
                        )
                    }
                    Metric("Uptime", Units.formatUptime(t?.uptimeSeconds))
                }
            }

            SectionCard("SHARES & POOL") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    Metric("Accepted", t?.sharesAccepted?.toString() ?: "—")
                    Metric("Rejected", t?.sharesRejected?.toString() ?: "—")
                    Metric("Best diff", Units.formatDifficulty(t?.bestDifficulty))
                    Metric("Session best", Units.formatDifficulty(t?.bestSessionDifficulty))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Pool: " + (t?.poolUrl?.let { "$it:${t.poolPort ?: "?"}" } ?: "—") +
                        if (t?.usingFallbackPool == true) "  (fallback active)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }

            SectionCard("IDENTITY") {
                InfoRow("Model", miner.identity.model)
                InfoRow("ASIC", miner.identity.asicModel)
                InfoRow("Firmware", listOfNotNull(miner.identity.firmwareFamily, miner.identity.firmwareVersion).joinToString(" "))
                InfoRow("MAC", miner.identity.macAddress)
                InfoRow("Serial", miner.identity.serialNumber)
                InfoRow("Address", "${miner.host}:${miner.port}")
            }

            state.healthScore?.let { health ->
                SectionCard("HEALTH SCORE") {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            "${health.score}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                health.score >= 85 -> HiBrand.statusOnline
                                health.score >= 60 -> HiBrand.statusDegraded
                                else -> HiBrand.statusOffline
                            },
                        )
                        Text(
                            " / 100",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HiBrand.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    health.reasons.forEach { reason ->
                        Text(
                            (if (reason.points > 0) "−${reason.points}  " else "") + reason.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (reason.points > 0) HiBrand.textSecondary else HiBrand.statusOnline,
                        )
                    }
                }
            }

            state.capabilities?.let { caps ->
                SectionCard("CONTROLS") {
                    ControlsCard(
                        capabilities = caps,
                        tuneOptions = state.tuneOptions,
                        currentPool = t?.poolUrl?.let { Triple(it, t.poolPort ?: 3333, t.workerName ?: "") },
                        currentFrequencyMhz = t?.frequencyMhz?.value?.toInt(),
                        currentVoltageMv = t?.coreVoltageMv?.value?.toInt(),
                        fanAutoNow = t?.autoFanEnabled,
                        fanPercentNow = t?.fans?.firstOrNull()?.percent,
                        hasTuneToRollback = state.hasTuneToRollback,
                        busyAction = state.busyAction,
                        lastActionMessage = state.lastActionMessage,
                        onReboot = viewModel::reboot,
                        onSetPool = viewModel::setPool,
                        onSetFanAuto = viewModel::setFanAuto,
                        onSetFanManual = viewModel::setFanManual,
                        onApplyTune = viewModel::applyTune,
                        onRollbackTune = viewModel::rollbackTune,
                    )
                }
            }

            if (state.alerts.isNotEmpty()) {
                SectionCard("RECENT ALERTS") {
                    state.alerts.take(6).forEach { alert ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Text(
                                alert.message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                color = if (alert.resolvedAtEpochMs == null) HiBrand.statusDegraded
                                else HiBrand.textSecondary,
                            )
                            Text(
                                if (alert.resolvedAtEpochMs == null) "active" else "resolved",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (alert.resolvedAtEpochMs == null) HiBrand.statusDegraded
                                else HiBrand.statusOnline,
                            )
                        }
                    }
                }
            }

            SectionCard(
                title = "RAW API RESPONSE" + if (state.showRaw) "" else "  (tap to show)",
                onClick = { viewModel.toggleRaw() },
            ) {
                if (state.showRaw) {
                    Text(
                        state.rawResponse ?: "No raw response captured yet",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = HiBrand.textSecondary,
                    )
                    Text(
                        "Credential and Wi-Fi fields are redacted.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.statusDegraded,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove miner?") },
            text = { Text("This removes ${miner?.name ?: "this miner"} and keeps no telemetry history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteMiner(onDeleted = onBack)
                }) { Text("Remove", color = HiBrand.statusOffline) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(14.dp),
        onClick = onClick ?: {},
        enabled = true,
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary)
        Text(
            value?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun lastReadingLabel(ts: Instant?): String {
    if (ts == null) return "No successful reading yet"
    val secs = Duration.between(ts, Instant.now()).seconds
    return if (secs < 90) "Last reading ${secs}s ago" else "Last reading ${secs / 60}m ago — stale"
}
