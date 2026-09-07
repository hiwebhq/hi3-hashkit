package hi3.hashkit.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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

// Section order: alphabetical, with DATA & EXPORTS second-to-last and DEMO last
// (user preference).
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
            Section("ABOUT") {
                val context = androidx.compose.ui.platform.LocalContext.current
                val version = remember {
                    runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull() ?: "?"
                }
                fun open(url: String) {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url),
                            )
                        )
                    }
                }
                Text(
                    "${HiBrand.appName}  ·  v$version",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Hi3 Hashkit is a local-first Android dashboard for Bitcoin miners: it " +
                        "discovers, monitors, and safely controls your fleet on your own " +
                        "network — no account, no cloud, your data stays on your device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                LinkRow(
                    title = "Hi3",
                    subtitle = "The Hi3 mining platform",
                    linkLabel = "hi3.cc",
                    highlight = false,
                ) { open("https://www.hi3.cc") }
                LinkRow(
                    title = "Bitcoin pool services",
                    subtitle = "Solo, PPLNS and TIDES payouts on Hi3 Pool",
                    linkLabel = "pool.hi3.cc",
                    highlight = true,
                ) { open("https://pool.hi3.cc") }
                LinkRow(
                    title = "Help & support",
                    subtitle = "Assistance with this app and fleet management",
                    linkLabel = "mmp.hi3.cc",
                    highlight = false,
                ) { open("https://mmp.hi3.cc") }
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

            Section("DISPLAY") {
                Text("Theme", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        hi3.hashkit.ui.theme.ThemeMode.SYSTEM to "System",
                        hi3.hashkit.ui.theme.ThemeMode.DARK to "Dark",
                        hi3.hashkit.ui.theme.ThemeMode.LIGHT to "Light",
                    ).forEach { (mode, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(label) },
                        )
                    }
                }
                ToggleRow(
                    "Solo odds card",
                    "Show block-finding probability on the dashboard.",
                    settings.showSoloCard,
                ) { viewModel.setShowSoloCard(it) }
            }

            Section("HI3 MMP") {
                ToggleRow(
                    "Hi3 MMP fleet view",
                    "Read-only fleet summary and per-site rollups from your Mining " +
                        "Management Platform.",
                    settings.mmpEnabled,
                ) { viewModel.setMmpEnabled(it) }
                if (settings.mmpEnabled) {
                    NumberRow("MMP URL", settings.mmpBaseUrl) { viewModel.setMmpBaseUrl(it) }
                    var keyInput by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = {
                            Text(
                                if (settings.mmpKeyConfigured) "API key (saved — enter to replace, blank to clear)"
                                else "API key (mint one in the MMP admin UI)",
                            )
                        },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.setMmpApiKey(keyInput)
                        keyInput = ""
                    }) { Text(if (settings.mmpKeyConfigured) "Replace key" else "Save key") }
                }
                Text(
                    "What is transmitted while enabled: your MMP API key in the request " +
                        "header, over HTTPS to the MMP URL above, about once a minute while " +
                        "the app is open — read-only fleet queries, nothing uploaded. The key " +
                        "is stored encrypted with the Android Keystore. Off by default.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }

            Section("HI3 POOL") {
                ToggleRow(
                    "Hi3 Pool stats",
                    "Read-only pool-side view of your workers, compared against local " +
                        "miner readings on the dashboard.",
                    settings.hi3PoolEnabled,
                ) { viewModel.setHi3PoolEnabled(it) }
                if (settings.hi3PoolEnabled) {
                    NumberRow("Pool URL", settings.hi3PoolBaseUrl) { viewModel.setHi3PoolBaseUrl(it) }
                    NumberRow("Payout address (account key)", settings.hi3PoolPayoutAddress) {
                        viewModel.setHi3PoolPayoutAddress(it)
                    }
                }
                Text(
                    "What is transmitted while enabled: your payout address, inside an " +
                        "HTTPS request to the pool URL above, about once a minute while the " +
                        "app is open. Nothing else — no miner telemetry, no local IPs, no " +
                        "worker passwords. Off by default; turning it off stops all pool " +
                        "requests immediately.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }

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

            Section("SECURITY") {
                val context = androidx.compose.ui.platform.LocalContext.current
                val canLock = remember {
                    androidx.biometric.BiometricManager.from(context).canAuthenticate(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
                }
                ToggleRow(
                    "App lock",
                    if (canLock)
                        "Require fingerprint/face or your device PIN to open the app " +
                            "(relocks after 1 minute in the background)."
                    else
                        "Unavailable: set up a screen lock (PIN/biometric) on this device first.",
                    settings.appLockEnabled && canLock,
                ) { if (canLock) viewModel.setAppLockEnabled(it) }
                Text(
                    "Protects the app UI. Note: exported backups and the on-disk database " +
                        "are protected by Android's standard app sandboxing, not by this lock.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
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

            Section("UNITS & COST") {
                ToggleRow("Fahrenheit", "Show temperatures in °F.", settings.useFahrenheit) {
                    viewModel.setUseFahrenheit(it)
                }
                NumberRow("Electricity rate (per kWh)", settings.electricityRatePerKwh.toString()) {
                    it.toDoubleOrNull()?.let { v -> viewModel.setElectricityRate(v) }
                }
                NumberRow("Currency code", settings.currencyCode) { viewModel.setCurrencyCode(it) }
            }

            Section("DATA & EXPORTS") {
                val context = androidx.compose.ui.platform.LocalContext.current
                val restoreMessage by viewModel.restoreMessage.collectAsStateWithLifecycle()
                val pendingEnc by viewModel.pendingEncryptedRestore.collectAsStateWithLifecycle()
                var backupPassPrompt by remember { mutableStateOf(false) }
                val restorePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> uri?.let { viewModel.restoreFrom(it) } }

                fun share(intent: android.content.Intent, title: String) {
                    context.startActivity(android.content.Intent.createChooser(intent, title))
                }
                ActionRow("Export fleet telemetry CSV (last 7 days)") {
                    viewModel.exportFleetCsv { share(it, "Export CSV") }
                }
                ActionRow("Backup miners & schedules") { backupPassPrompt = true }
                ActionRow("Restore from backup…") {
                    restorePicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                }
                ActionRow("Export diagnostics bundle (addresses redacted)") {
                    viewModel.exportDiagnostics(includeAddresses = false) { share(it, "Export diagnostics") }
                }
                restoreMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.accentAlt)
                }
                Text(
                    "Backups contain miner addresses and worker names. Encrypt with a " +
                        "passphrase (AES-256) to share or store safely; a plaintext backup is " +
                        "for your own device only. CSV and diagnostics redact wallets; " +
                        "diagnostics also redact IP addresses.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )

                if (backupPassPrompt) {
                    var pass by remember { mutableStateOf("") }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { backupPassPrompt = false },
                        title = { Text("Backup passphrase") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Enter a passphrase to encrypt the backup (AES-256), or " +
                                        "leave blank for a plaintext JSON backup.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                OutlinedTextField(
                                    value = pass,
                                    onValueChange = { pass = it },
                                    label = { Text("Passphrase (optional)") },
                                    singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                )
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                backupPassPrompt = false
                                viewModel.exportBackup(pass) { share(it, "Export backup") }
                            }) { Text("Export") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { backupPassPrompt = false }) { Text("Cancel") }
                        },
                    )
                }

                pendingEnc?.let { uri ->
                    var pass by remember { mutableStateOf("") }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { viewModel.pendingEncryptedRestore.value = null },
                        title = { Text("Encrypted backup") },
                        text = {
                            OutlinedTextField(
                                value = pass,
                                onValueChange = { pass = it },
                                label = { Text("Passphrase") },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                viewModel.restoreFrom(uri, pass)
                            }) { Text("Restore") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { viewModel.pendingEncryptedRestore.value = null }) { Text("Cancel") }
                        },
                    )
                }
            }

            Section("DEMO") {
                ToggleRow(
                    "Demo mode",
                    "Adds clearly-labeled synthetic miners; they never mix with real totals when off.",
                    settings.demoModeEnabled,
                ) { viewModel.setDemoMode(it) }
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
private fun LinkRow(
    title: String,
    subtitle: String,
    linkLabel: String,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                color = if (highlight) HiBrand.accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
        }
        Text(
            "$linkLabel ↗",
            style = MaterialTheme.typography.labelLarge,
            color = HiBrand.accent,
            fontWeight = FontWeight.SemiBold,
        )
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
