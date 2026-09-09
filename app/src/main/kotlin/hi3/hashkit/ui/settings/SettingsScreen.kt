package hi3.hashkit.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
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
import hi3.hashkit.ui.theme.ThemeColor

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

            Section("QUIET HOURS & DIGEST") {
                ToggleRow(
                    "Quiet hours",
                    "Suppress alert notifications during a nightly window. Alerts are still " +
                        "recorded and appear in the daily digest.",
                    settings.quietHoursEnabled,
                ) { viewModel.setQuietHoursEnabled(it) }
                if (settings.quietHoursEnabled) {
                    NumberRow("Quiet from (HH:MM)", minutesToHhMm(settings.quietStartMinute)) {
                        hhMmToMinutes(it)?.let { m -> viewModel.setQuietStartMinute(m) }
                    }
                    NumberRow("Quiet until (HH:MM)", minutesToHhMm(settings.quietEndMinute)) {
                        hhMmToMinutes(it)?.let { m -> viewModel.setQuietEndMinute(m) }
                    }
                }
                ToggleRow(
                    "Daily digest",
                    "One summary notification per day of the last 24h of alerts.",
                    settings.digestEnabled,
                ) { viewModel.setDigestEnabled(it) }
                if (settings.digestEnabled) {
                    NumberRow("Digest time (hour, 0–23)", settings.digestHour.toString()) {
                        it.toIntOrNull()?.let { v -> viewModel.setDigestHour(v) }
                    }
                }
            }

            Section("PUSH (WEBHOOK)") {
                Text(
                    "Mirror alerts to your own push service so they reach you when the app is " +
                        "closed — no cloud account of ours. Fires only for active alerts.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                WebhookTypeRow(current = settings.webhookType, onSelect = { viewModel.setWebhookType(it) })
                when (settings.webhookType) {
                    hi3.hashkit.data.alerts.WebhookType.NONE -> Unit
                    hi3.hashkit.data.alerts.WebhookType.NTFY ->
                        NumberRow("ntfy topic URL (https://ntfy.sh/your-topic)", settings.webhookUrl) { viewModel.setWebhookUrl(it) }
                    hi3.hashkit.data.alerts.WebhookType.GOTIFY -> {
                        NumberRow("Gotify server URL", settings.webhookUrl) { viewModel.setWebhookUrl(it) }
                        NumberRow("Gotify app token", settings.webhookToken) { viewModel.setWebhookToken(it) }
                    }
                    hi3.hashkit.data.alerts.WebhookType.TELEGRAM -> {
                        NumberRow("Telegram bot token", settings.webhookToken) { viewModel.setWebhookToken(it) }
                        NumberRow("Telegram chat ID", settings.webhookTarget) { viewModel.setWebhookTarget(it) }
                    }
                    hi3.hashkit.data.alerts.WebhookType.GENERIC ->
                        NumberRow("POST URL (JSON body)", settings.webhookUrl) { viewModel.setWebhookUrl(it) }
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
                Spacer(Modifier.height(4.dp))
                Text("UI Theme", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Accent color used across the app. Status colors (online/offline) don't change.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                ThemeColorRow(
                    selected = settings.themeColor,
                    onSelect = { viewModel.setThemeColor(it) },
                )
                ToggleRow(
                    "Profitability & energy card",
                    "Show estimated revenue, power cost, and energy use on the dashboard.",
                    settings.showProfitCard,
                ) { viewModel.setShowProfitCard(it) }
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

            Section("POOL") {
                ToggleRow(
                    "Pool stats",
                    "Read-only pool-side view of your workers, correlated with local miner " +
                        "readings on the dashboard.",
                    settings.hi3PoolEnabled,
                ) { viewModel.setHi3PoolEnabled(it) }
                if (settings.hi3PoolEnabled) {
                    PoolTypeRow(current = settings.poolType, onSelect = { viewModel.setPoolType(it) })
                    if (settings.poolType.comingSoon) {
                        Text(
                            "${settings.poolType.displayName.removeSuffix(" (coming soon)")} support is on " +
                                "the way. Its API needs verifying before we send anything, so this pool " +
                                "contacts nothing yet — pick another pool to load live stats.",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiBrand.statusDegraded,
                        )
                    } else {
                        if (settings.poolType.baseUrlEditable) {
                            NumberRow("Pool URL", settings.hi3PoolBaseUrl) { viewModel.setHi3PoolBaseUrl(it) }
                        }
                        PayoutAddressRow(
                            label = settings.poolType.identifierLabel,
                            value = settings.hi3PoolPayoutAddress,
                            onChange = { viewModel.setHi3PoolPayoutAddress(it) },
                        )
                        NumberRow(
                            if (settings.poolType.usesToken) "Access token"
                            else "API key / watcher token (optional)",
                            settings.poolApiToken,
                        ) { viewModel.setPoolApiToken(it) }
                    }
                }
                if (!settings.poolType.comingSoon) {
                    Text(
                        "What is transmitted while enabled: your ${settings.poolType.identifierLabel.lowercase()} " +
                            "(and token if set), inside an HTTPS request to ${settings.poolType.displayName}, " +
                            "about once a minute while the app is open. Nothing else — no miner telemetry, " +
                            "no local IPs, no worker passwords. Off by default; turning it off stops all " +
                            "pool requests immediately.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
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
                val farms by viewModel.farms.collectAsStateWithLifecycle()
                IntervalRow(
                    label = if (farms.isEmpty()) "Refresh interval" else "Default refresh interval",
                    currentMs = settings.pollIntervalMs,
                ) { viewModel.setDefaultRefreshIntervalMs(it) }
                if (farms.isNotEmpty()) {
                    Text(
                        "Each farm refreshes at its own interval while you're viewing it; the " +
                            "default applies when no farm is active.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                    farms.forEach { farm ->
                        IntervalRow(
                            label = "· ${farm.name}",
                            currentMs = farm.refreshIntervalMs,
                        ) { viewModel.setFarmRefreshIntervalMs(farm.id, it) }
                    }
                }
                NumberRow("Keep history (days)", settings.retentionDays.toString()) {
                    it.toIntOrNull()?.let { d -> viewModel.setRetentionDays(d) }
                }
                ToggleRow(
                    "Check for firmware updates",
                    "Opt-in: checks AxeOS (Bitaxe) releases on GitHub and flags miners that " +
                        "are behind. No flashing from the app; nothing is contacted while off.",
                    settings.firmwareUpdateCheck,
                ) { viewModel.setFirmwareUpdateCheck(it) }
                ToggleRow(
                    "Auto-recover offline miners",
                    "When a miner stays offline past the threshold, power-cycle its smart plug " +
                        "(if set) or reboot it — once, with a cooldown. Every attempt is audited.",
                    settings.autoRecoverEnabled,
                ) { viewModel.setAutoRecoverEnabled(it) }
                if (settings.autoRecoverEnabled) {
                    NumberRow("Recover after offline (min)", settings.autoRecoverAfterMin.toString()) {
                        it.toLongOrNull()?.let { v -> viewModel.setAutoRecoverAfterMin(v) }
                    }
                }
                ToggleRow(
                    "Always-on safety monitor",
                    "Runs the smart-plug over-temp cutoff in the background (with a persistent " +
                        "notification) even when the app is closed, and re-arms after a reboot. " +
                        "Only useful if you've set a plug cutoff on a miner.",
                    settings.safetyServiceEnabled,
                ) { enabled ->
                    viewModel.setSafetyService(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
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

            Section("ADVANCED FEATURES") {
                val unlocked = settings.advancedUnlocked
                Text(
                    if (unlocked)
                        "Advanced features are unlocked. This reveals the dashboard menu's " +
                            "\"Live Bitcoin\" link; more may move here in future versions " +
                            "(secure pages, power tools)."
                    else
                        "Enter your unlock code to reveal advanced features (currently the " +
                            "dashboard menu's \"Live Bitcoin\" link).",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
                var codeInput by remember { mutableStateOf(settings.advancedUnlockCode) }
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text(if (unlocked) "Unlock code (optional)" else "Unlock code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.setAdvancedUnlockCode(codeInput)
                }) { Text("Apply code") }
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
                NumberRow(
                    "BTC price (per coin, ${settings.currencyCode})",
                    if (settings.btcPrice > 0) "%.0f".format(settings.btcPrice) else "",
                ) { it.toDoubleOrNull()?.let { v -> viewModel.setBtcPrice(v) } }
                ToggleRow(
                    "Fetch BTC price from mempool.space",
                    "Opt-in: a single HTTPS GET (no miner data) so the dashboard can estimate " +
                        "revenue and profit. Off by default; enter a price manually instead if you prefer.",
                    settings.btcPriceAutoFetch,
                ) { viewModel.setBtcPriceAutoFetch(it) }
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

private fun minutesToHhMm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

private fun hhMmToMinutes(text: String): Int? {
    val parts = text.split(":")
    val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/** Six selectable accent swatches; the current one gets a ring. */
@Composable
private fun ThemeColorRow(selected: ThemeColor, onSelect: (ThemeColor) -> Unit) {
    val dark = isSystemInDarkTheme()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ThemeColor.entries.forEach { color ->
            val isSelected = color == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.accent(dark))
                        .then(
                            if (isSelected)
                                Modifier.border(3.dp, HiBrand.textPrimary, CircleShape)
                            else
                                Modifier.border(1.dp, HiBrand.outline, CircleShape)
                        )
                        .clickable { onSelect(color) },
                )
                Text(
                    color.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) HiBrand.textPrimary else HiBrand.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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

/** Payout-address field with an on-demand QR scanner (offline; no image leaves the device). */
@Composable
private fun PoolTypeRow(
    current: hi3.hashkit.integrations.hi3.PoolType,
    onSelect: (hi3.hashkit.integrations.hi3.PoolType) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Pool", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            androidx.compose.material3.AssistChip(
                onClick = { open = true },
                label = { Text(current.displayName) },
            )
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                hi3.hashkit.integrations.hi3.PoolType.entries.forEach { type ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(type.displayName) },
                        onClick = { open = false; onSelect(type) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WebhookTypeRow(
    current: hi3.hashkit.data.alerts.WebhookType,
    onSelect: (hi3.hashkit.data.alerts.WebhookType) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Push service", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            androidx.compose.material3.AssistChip(onClick = { open = true }, label = { Text(current.label) })
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                hi3.hashkit.data.alerts.WebhookType.entries.forEach { t ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(t.label) },
                        onClick = { open = false; onSelect(t) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PayoutAddressRow(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scan = rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        val scanned = result.contents?.trim()
        when {
            scanned.isNullOrEmpty() -> Unit // cancelled
            // Accept bare addresses only — reject bitcoin: URIs, query params or whitespace.
            scanned.contains(':') || scanned.contains('?') || scanned.any { it.isWhitespace() } ->
                android.widget.Toast.makeText(
                    context,
                    "That QR isn't a bare payout address (looks like a bitcoin: URI). Scan the plain address.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            else -> {
                text = scanned
                onChange(scanned)
            }
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it.trim()) },
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = {
                scan.launch(
                    com.journeyapps.barcodescanner.ScanOptions()
                        .setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                        .setPrompt("Scan your payout-address QR")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                )
            }) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan QR code")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Preset refresh intervals from 5 seconds to 1 day. */
private val INTERVAL_PRESETS: List<Pair<String, Long>> = listOf(
    "5 sec" to 5_000L, "10 sec" to 10_000L, "15 sec" to 15_000L, "30 sec" to 30_000L,
    "1 min" to 60_000L, "2 min" to 120_000L, "5 min" to 300_000L, "15 min" to 900_000L,
    "30 min" to 1_800_000L, "1 hour" to 3_600_000L, "6 hours" to 21_600_000L,
    "12 hours" to 43_200_000L, "1 day" to 86_400_000L,
)

private fun intervalLabel(ms: Long): String =
    INTERVAL_PRESETS.firstOrNull { it.second == ms }?.first
        ?: when {
            ms % 86_400_000L == 0L -> "${ms / 86_400_000L} day"
            ms % 3_600_000L == 0L -> "${ms / 3_600_000L} hour"
            ms % 60_000L == 0L -> "${ms / 60_000L} min"
            else -> "${ms / 1000L} sec"
        }

@Composable
private fun IntervalRow(label: String, currentMs: Long, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            androidx.compose.material3.AssistChip(
                onClick = { open = true },
                label = { Text(intervalLabel(currentMs)) },
            )
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                INTERVAL_PRESETS.forEach { (text, ms) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(text) },
                        onClick = { open = false; onSelect(ms) },
                    )
                }
            }
        }
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
