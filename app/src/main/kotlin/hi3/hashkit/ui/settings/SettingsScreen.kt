package hi3.hashkit.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.ui.theme.HiBrand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* worker/notifier check the grant themselves */ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("MONITORING") {
                ToggleRow(
                    "Background monitoring",
                    "Poll every ~15 min while the app is closed (Android may delay this; " +
                        "it is not continuous real-time monitoring).",
                    settings.backgroundMonitoringEnabled,
                ) { enabled ->
                    viewModel.setBackgroundMonitoring(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                NumberRow(
                    "Foreground poll interval (s)",
                    (settings.pollIntervalMs / 1000).toString(),
                ) { it.toLongOrNull()?.let { s -> viewModel.setPollIntervalSeconds(s) } }
                NumberRow("Keep history (days)", settings.retentionDays.toString()) {
                    it.toIntOrNull()?.let { d -> viewModel.setRetentionDays(d) }
                }
            }

            Section("ALERTS") {
                ToggleRow(
                    "Alerts & notifications",
                    "Offline, temperature, fan, reject-rate and restart alerts with recovery notices.",
                    settings.alertsEnabled,
                ) { enabled ->
                    viewModel.setAlertsEnabled(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                NumberRow("Hashrate alert below (% of expected)", settings.alertThresholds.hashrateBelowPercent.toInt().toString()) {
                    it.toDoubleOrNull()?.let { v -> viewModel.setHashrateBelowPercent(v) }
                }
                NumberRow("Chip temp alert (°C)", settings.alertThresholds.chipTempC.toInt().toString()) {
                    it.toDoubleOrNull()?.let { v -> viewModel.setChipTempThreshold(v) }
                }
                NumberRow("VR temp alert (°C)", settings.alertThresholds.vrTempC.toInt().toString()) {
                    it.toDoubleOrNull()?.let { v -> viewModel.setVrTempThreshold(v) }
                }
                NumberRow("Reject-rate alert (%)", settings.alertThresholds.rejectRatePercent.toString()) {
                    it.toDoubleOrNull()?.let { v -> viewModel.setRejectRateThreshold(v) }
                }
                NumberRow("Re-notify cooldown (min)", (settings.alertThresholds.cooldownMs / 60000).toString()) {
                    it.toLongOrNull()?.let { v -> viewModel.setCooldownMinutes(v) }
                }
            }

            Section("UNITS & COST") {
                ToggleRow("Fahrenheit", "Show temperatures in °F.", settings.useFahrenheit) {
                    viewModel.setUseFahrenheit(it)
                }
                NumberRow("Electricity rate (per kWh)", settings.electricityRatePerKwh.toString()) {
                    it.toDoubleOrNull()?.let { v -> viewModel.setElectricityRate(v) }
                }
                NumberRow("Currency code", settings.currencyCode) { viewModel.setCurrencyCode(it) }
            }

            Section("SOLO MINING") {
                NumberRow(
                    "Network difficulty",
                    if (settings.networkDifficulty > 0) "%.0f".format(settings.networkDifficulty) else "",
                ) { it.toDoubleOrNull()?.let { v -> viewModel.setNetworkDifficulty(v) } }
                ToggleRow(
                    "Fetch difficulty from mempool.space",
                    "The app's only external request; a single HTTPS GET carrying no miner " +
                        "data. Off by default — see docs/SECURITY.md.",
                    settings.difficultyAutoFetch,
                ) { viewModel.setDifficultyAutoFetch(it) }
            }

            Section("DISCOVERY") {
                NumberRow(
                    "Extra scan subnets (CSV of CIDRs)",
                    settings.extraSubnetsCsv,
                ) { viewModel.setExtraSubnets(it) }
                Text(
                    "Add remote LANs reachable through your Tailscale subnet router, e.g. " +
                        "192.168.50.0/24. They appear as quick-fill options on the Add screen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }

            Section("DATA & EXPORTS") {
                val context = androidx.compose.ui.platform.LocalContext.current
                val restoreMessage by viewModel.restoreMessage.collectAsStateWithLifecycle()
                val restorePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> uri?.let { viewModel.restoreFrom(it) } }

                fun share(intent: android.content.Intent, title: String) {
                    context.startActivity(android.content.Intent.createChooser(intent, title))
                }
                ActionRow("Export fleet telemetry CSV (last 7 days)") {
                    viewModel.exportFleetCsv { share(it, "Export CSV") }
                }
                ActionRow("Backup miners & schedules (JSON)") {
                    viewModel.exportBackup { share(it, "Export backup") }
                }
                ActionRow("Restore from backup…") {
                    restorePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
                ActionRow("Export diagnostics bundle (addresses redacted)") {
                    viewModel.exportDiagnostics(includeAddresses = false) { share(it, "Export diagnostics") }
                }
                restoreMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.accentAlt)
                }
                Text(
                    "Backups include miner addresses and worker names for your own restore — " +
                        "share the file only with yourself. CSV and diagnostics redact wallets; " +
                        "diagnostics also redact IP addresses.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }

            Section("DEMO") {
                ToggleRow(
                    "Demo mode",
                    "Adds clearly-labeled synthetic miners; they never mix with real totals when off.",
                    settings.demoModeEnabled,
                ) { viewModel.setDemoMode(it) }
            }

            Section("FUTURE INTEGRATIONS") {
                Text(
                    "Hi3 Pool (pool.hi3.cc) and Hi3 MMP (mmp.hi3.cc) integrations are " +
                        "planned but disabled — this version never contacts Hi3 servers. " +
                        "When they ship they will be opt-in and will state exactly what is " +
                        "transmitted before enabling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

@Composable
private fun NumberRow(label: String, initial: String, onCommit: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onCommit(it.trim())
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(0.dp))
}
