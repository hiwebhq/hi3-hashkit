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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.R
import hi3.hashkit.ui.theme.HiBrand
import hi3.hashkit.ui.util.launchChooser
import hi3.hashkit.ui.theme.ThemeColor

// Section order: alphabetical, with DATA & EXPORTS second-to-last and DEMO last
// (user preference).
@Suppress("LongMethod", "CyclomaticComplexMethod") // a declarative settings form: one block per section
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onFarms: () -> Unit = {},
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
                title = { Text(stringResource(R.string.common_settings), fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(stringResource(R.string.set_section_advanced)) {
                val unlocked = settings.advancedUnlocked
                Text(
                    if (unlocked)
                        stringResource(R.string.set_advanced_unlocked_desc)
                    else
                        stringResource(R.string.set_advanced_locked_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
                var codeInput by remember { mutableStateOf(settings.advancedUnlockCode) }
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = {
                        Text(
                            if (unlocked) stringResource(R.string.set_advanced_unlock_code_optional)
                            else stringResource(R.string.set_advanced_unlock_code)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.setAdvancedUnlockCode(codeInput)
                }) { Text(stringResource(R.string.set_advanced_apply_code)) }
            }

            Section(stringResource(R.string.set_section_alerts)) {
                ToggleRow(
                    stringResource(R.string.set_alerts_title),
                    stringResource(R.string.set_alerts_subtitle),
                    settings.alertsEnabled,
                ) { enabled ->
                    viewModel.setAlertsEnabled(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                NumberRow(stringResource(R.string.set_alert_hashrate_below), settings.alertThresholds.hashrateBelowPercent.toInt().toString()) {
                    it.toDoubleOrNull()?.let { v -> viewModel.setHashrateBelowPercent(v) }
                }
                NumberRow(stringResource(R.string.set_alert_chip_temp), settings.alertThresholds.chipTempC.toInt().toString()) {
                    it.toDoubleOrNull()?.let { v -> viewModel.setChipTempThreshold(v) }
                }
                NumberRow(stringResource(R.string.set_alert_vr_temp), settings.alertThresholds.vrTempC.toInt().toString()) {
                    it.toDoubleOrNull()?.let { v -> viewModel.setVrTempThreshold(v) }
                }
                NumberRow(stringResource(R.string.set_alert_reject_rate), settings.alertThresholds.rejectRatePercent.toString()) {
                    it.toDoubleOrNull()?.let { v -> viewModel.setRejectRateThreshold(v) }
                }
                NumberRow(stringResource(R.string.set_alert_cooldown), (settings.alertThresholds.cooldownMs / 60000).toString()) {
                    it.toLongOrNull()?.let { v -> viewModel.setCooldownMinutes(v) }
                }
            }

            Section(stringResource(R.string.set_section_discovery)) {
                NumberRow(
                    stringResource(R.string.set_discovery_extra_subnets),
                    settings.extraSubnetsCsv,
                ) { viewModel.setExtraSubnets(it) }
                Text(
                    stringResource(R.string.set_discovery_subnets_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }

            Section(stringResource(R.string.set_section_display)) {
                Text(
                    androidx.compose.ui.res.stringResource(hi3.hashkit.R.string.language_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    androidx.compose.ui.res.stringResource(hi3.hashkit.R.string.language_settings_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                hi3.hashkit.ui.language.LanguagePicker()
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        hi3.hashkit.ui.theme.ThemeMode.SYSTEM to stringResource(R.string.set_theme_system),
                        hi3.hashkit.ui.theme.ThemeMode.DARK to stringResource(R.string.set_theme_dark),
                        hi3.hashkit.ui.theme.ThemeMode.LIGHT to stringResource(R.string.set_theme_light),
                    ).forEach { (mode, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.set_ui_theme), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.set_ui_theme_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                ThemeColorRow(
                    selected = settings.themeColor,
                    onSelect = { viewModel.setThemeColor(it) },
                )
                ToggleRow(
                    stringResource(R.string.set_profit_card_title),
                    stringResource(R.string.set_profit_card_subtitle),
                    settings.showProfitCard,
                ) { viewModel.setShowProfitCard(it) }
                ToggleRow(
                    stringResource(R.string.set_solo_card_title),
                    stringResource(R.string.set_solo_card_subtitle),
                    settings.showSoloCard,
                ) { viewModel.setShowSoloCard(it) }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.set_card_order_title), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.set_card_order_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                val cardOrder = hi3.hashkit.ui.dashboard.DashboardCard.orderFrom(settings.dashboardCardOrder)
                cardOrder.forEachIndexed { i, card ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${i + 1}.  ${stringResource(card.labelRes)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        androidx.compose.material3.TextButton(
                            enabled = i > 0,
                            onClick = {
                                val next = cardOrder.toMutableList().apply { add(i - 1, removeAt(i)) }
                                viewModel.setDashboardCardOrder(
                                    hi3.hashkit.ui.dashboard.DashboardCard.toCsv(next)
                                )
                            },
                        ) { Text("↑") }
                        androidx.compose.material3.TextButton(
                            enabled = i < cardOrder.size - 1,
                            onClick = {
                                val next = cardOrder.toMutableList().apply { add(i + 1, removeAt(i)) }
                                viewModel.setDashboardCardOrder(
                                    hi3.hashkit.ui.dashboard.DashboardCard.toCsv(next)
                                )
                            },
                        ) { Text("↓") }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.set_inventory_tag_title), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.set_inventory_tag_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        hi3.hashkit.data.prefs.InventoryTagType.QR to stringResource(R.string.set_tag_qr),
                        hi3.hashkit.data.prefs.InventoryTagType.NFC to stringResource(R.string.set_tag_nfc),
                        hi3.hashkit.data.prefs.InventoryTagType.BOTH to stringResource(R.string.set_tag_both),
                    ).forEach { (type, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = settings.inventoryTagType == type,
                            onClick = { viewModel.setInventoryTagType(type) },
                            label = { Text(label) },
                        )
                    }
                }
                ToggleRow(
                    stringResource(R.string.set_pause_on_exit_title),
                    stringResource(R.string.set_pause_on_exit_subtitle),
                    settings.confirmBeforeExit,
                ) { viewModel.setConfirmBeforeExit(it) }
            }

            Section(stringResource(R.string.set_section_farms)) {
                ActionRow(stringResource(R.string.set_manage_farms)) { onFarms() }
                val farms by viewModel.farms.collectAsStateWithLifecycle()
                if (farms.isNotEmpty()) {
                    Text(stringResource(R.string.set_default_farm), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.set_default_farm_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.FilterChip(
                            selected = settings.pinnedFarmId <= 0 ||
                                farms.none { it.id == settings.pinnedFarmId },
                            onClick = { viewModel.setPinnedFarm(-1) },
                            label = { Text(stringResource(R.string.set_all_farms)) },
                        )
                        farms.forEach { farm ->
                            androidx.compose.material3.FilterChip(
                                selected = settings.pinnedFarmId == farm.id,
                                onClick = { viewModel.setPinnedFarm(farm.id) },
                                label = { Text(farm.name) },
                            )
                        }
                    }
                }
            }

            Section(stringResource(R.string.set_section_mmp)) {
                ToggleRow(
                    stringResource(R.string.set_mmp_title),
                    stringResource(R.string.set_mmp_subtitle),
                    settings.mmpEnabled,
                ) { viewModel.setMmpEnabled(it) }
                if (settings.mmpEnabled) {
                    NumberRow(stringResource(R.string.set_mmp_url), settings.mmpBaseUrl) { viewModel.setMmpBaseUrl(it) }
                    var keyInput by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = {
                            Text(
                                if (settings.mmpKeyConfigured) stringResource(R.string.set_mmp_key_saved)
                                else stringResource(R.string.set_mmp_key_hint),
                            )
                        },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.setMmpApiKey(keyInput)
                        keyInput = ""
                    }) {
                        Text(
                            if (settings.mmpKeyConfigured) stringResource(R.string.set_mmp_replace_key)
                            else stringResource(R.string.set_mmp_save_key)
                        )
                    }
                }
                Text(
                    stringResource(R.string.set_mmp_disclosure),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }

            if (settings.advancedUnlocked) {
                Section(stringResource(R.string.set_section_web_server)) {
                    ToggleRow(
                        stringResource(R.string.set_web_server_title),
                        stringResource(R.string.set_web_server_subtitle),
                        settings.prometheusEnabled,
                    ) { viewModel.setPrometheusEnabled(it) }
                    if (settings.prometheusEnabled) {
                        NumberRow(stringResource(R.string.set_web_server_port), settings.prometheusPort.toString()) {
                            it.toIntOrNull()?.let { v -> viewModel.setPrometheusPort(v) }
                        }
                        Text(
                            stringResource(R.string.set_web_server_hint, settings.prometheusPort),
                            style = MaterialTheme.typography.labelSmall,
                            color = HiBrand.textSecondary,
                        )
                    }
                }
            }

            Section(stringResource(R.string.set_section_monitoring)) {
                ToggleRow(
                    stringResource(R.string.set_bg_monitoring_title),
                    stringResource(R.string.set_bg_monitoring_subtitle),
                    settings.backgroundMonitoringEnabled,
                ) { enabled ->
                    viewModel.setBackgroundMonitoring(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                val farms by viewModel.farms.collectAsStateWithLifecycle()
                IntervalRow(
                    label = if (farms.isEmpty()) stringResource(R.string.set_refresh_interval)
                    else stringResource(R.string.set_default_refresh_interval),
                    currentMs = settings.pollIntervalMs,
                ) { viewModel.setDefaultRefreshIntervalMs(it) }
                if (farms.isNotEmpty()) {
                    Text(
                        stringResource(R.string.set_farm_refresh_hint),
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
                NumberRow(stringResource(R.string.set_keep_history), settings.retentionDays.toString()) {
                    it.toIntOrNull()?.let { d -> viewModel.setRetentionDays(d) }
                }
                ToggleRow(
                    stringResource(R.string.set_fw_check_title),
                    stringResource(R.string.set_fw_check_subtitle),
                    settings.firmwareUpdateCheck,
                ) { viewModel.setFirmwareUpdateCheck(it) }
                ToggleRow(
                    stringResource(R.string.set_auto_recover_title),
                    stringResource(R.string.set_auto_recover_subtitle),
                    settings.autoRecoverEnabled,
                ) { viewModel.setAutoRecoverEnabled(it) }
                if (settings.autoRecoverEnabled) {
                    NumberRow(stringResource(R.string.set_auto_recover_after), settings.autoRecoverAfterMin.toString()) {
                        it.toLongOrNull()?.let { v -> viewModel.setAutoRecoverAfterMin(v) }
                    }
                }
                ToggleRow(
                    stringResource(R.string.set_safety_title),
                    stringResource(R.string.set_safety_subtitle),
                    settings.safetyServiceEnabled,
                ) { enabled ->
                    viewModel.setSafetyService(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            Section(stringResource(R.string.set_section_mqtt)) {
                ToggleRow(
                    stringResource(R.string.set_mqtt_title),
                    stringResource(R.string.set_mqtt_subtitle),
                    settings.mqttEnabled,
                ) { viewModel.setMqttEnabled(it) }
                if (settings.mqttEnabled) {
                    NumberRow(stringResource(R.string.set_mqtt_host), settings.mqttHost) { viewModel.setMqttHost(it) }
                    NumberRow(stringResource(R.string.set_mqtt_port), settings.mqttPort.toString()) {
                        it.toIntOrNull()?.let { v -> viewModel.setMqttPort(v) }
                    }
                    NumberRow(stringResource(R.string.set_mqtt_topic), settings.mqttBaseTopic) { viewModel.setMqttBaseTopic(it) }
                    NumberRow(stringResource(R.string.set_mqtt_username), settings.mqttUsername) { viewModel.setMqttUsername(it) }
                    var mqttPass by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = mqttPass,
                        onValueChange = { mqttPass = it },
                        label = {
                            Text(
                                if (settings.mqttPasswordConfigured) stringResource(R.string.set_mqtt_password_saved)
                                else stringResource(R.string.set_mqtt_password_optional),
                            )
                        },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.setMqttPassword(mqttPass)
                        mqttPass = ""
                    }) {
                        Text(
                            if (settings.mqttPasswordConfigured) stringResource(R.string.set_mqtt_replace_password)
                            else stringResource(R.string.set_mqtt_save_password)
                        )
                    }
                    ToggleRow(
                        stringResource(R.string.set_mqtt_ha_title),
                        stringResource(R.string.set_mqtt_ha_subtitle),
                        settings.mqttHomeAssistantDiscovery,
                    ) { viewModel.setMqttHaDiscovery(it) }
                    Text(
                        stringResource(R.string.set_mqtt_disclosure),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }

            Section(stringResource(R.string.set_section_pool)) {
                ToggleRow(
                    stringResource(R.string.set_pool_stats_title),
                    stringResource(R.string.set_pool_stats_subtitle),
                    settings.hi3PoolEnabled,
                ) { viewModel.setHi3PoolEnabled(it) }
                if (settings.hi3PoolEnabled) {
                    PoolTypeRow(current = settings.poolType, onSelect = { viewModel.setPoolType(it) })
                    if (settings.poolType.comingSoon) {
                        Text(
                            stringResource(
                                R.string.set_pool_coming_soon,
                                settings.poolType.displayName.removeSuffix(" (coming soon)"),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = HiBrand.statusDegraded,
                        )
                    } else {
                        if (settings.poolType.baseUrlEditable) {
                            NumberRow(stringResource(R.string.set_pool_url), settings.hi3PoolBaseUrl) { viewModel.setHi3PoolBaseUrl(it) }
                        }
                        PayoutAddressRow(
                            label = settings.poolType.identifierLabel,
                            value = settings.hi3PoolPayoutAddress,
                            onChange = { viewModel.setHi3PoolPayoutAddress(it) },
                        )
                        NumberRow(
                            if (settings.poolType.usesToken) stringResource(R.string.set_pool_access_token)
                            else stringResource(R.string.set_pool_api_token),
                            settings.poolApiToken,
                        ) { viewModel.setPoolApiToken(it) }
                    }
                }
                if (!settings.poolType.comingSoon) {
                    Text(
                        stringResource(
                            R.string.set_pool_disclosure,
                            settings.poolType.identifierLabel.lowercase(),
                            settings.poolType.displayName,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }

            Section(stringResource(R.string.set_section_push)) {
                Text(
                    stringResource(R.string.set_push_intro),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                WebhookTypeRow(current = settings.webhookType, onSelect = { viewModel.setWebhookType(it) })
                when (settings.webhookType) {
                    hi3.hashkit.data.alerts.WebhookType.NONE -> Unit
                    hi3.hashkit.data.alerts.WebhookType.NTFY ->
                        NumberRow(stringResource(R.string.set_push_ntfy_url), settings.webhookUrl) { viewModel.setWebhookUrl(it) }
                    hi3.hashkit.data.alerts.WebhookType.GOTIFY -> {
                        NumberRow(stringResource(R.string.set_push_gotify_url), settings.webhookUrl) { viewModel.setWebhookUrl(it) }
                        NumberRow(stringResource(R.string.set_push_gotify_token), settings.webhookToken) { viewModel.setWebhookToken(it) }
                    }
                    hi3.hashkit.data.alerts.WebhookType.TELEGRAM -> {
                        NumberRow(stringResource(R.string.set_push_telegram_token), settings.webhookToken) { viewModel.setWebhookToken(it) }
                        NumberRow(stringResource(R.string.set_push_telegram_chat), settings.webhookTarget) { viewModel.setWebhookTarget(it) }
                    }
                    hi3.hashkit.data.alerts.WebhookType.GENERIC ->
                        NumberRow(stringResource(R.string.set_push_generic_url), settings.webhookUrl) { viewModel.setWebhookUrl(it) }
                }
            }

            Section(stringResource(R.string.set_section_quiet)) {
                ToggleRow(
                    stringResource(R.string.set_quiet_hours_title),
                    stringResource(R.string.set_quiet_hours_subtitle),
                    settings.quietHoursEnabled,
                ) { viewModel.setQuietHoursEnabled(it) }
                if (settings.quietHoursEnabled) {
                    NumberRow(stringResource(R.string.set_quiet_from), minutesToHhMm(settings.quietStartMinute)) {
                        hhMmToMinutes(it)?.let { m -> viewModel.setQuietStartMinute(m) }
                    }
                    NumberRow(stringResource(R.string.set_quiet_until), minutesToHhMm(settings.quietEndMinute)) {
                        hhMmToMinutes(it)?.let { m -> viewModel.setQuietEndMinute(m) }
                    }
                }
                ToggleRow(
                    stringResource(R.string.set_digest_title),
                    stringResource(R.string.set_digest_subtitle),
                    settings.digestEnabled,
                ) { viewModel.setDigestEnabled(it) }
                if (settings.digestEnabled) {
                    NumberRow(stringResource(R.string.set_digest_time), settings.digestHour.toString()) {
                        it.toIntOrNull()?.let { v -> viewModel.setDigestHour(v) }
                    }
                }
            }

            Section(stringResource(R.string.set_section_security)) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val canLock = remember {
                    androidx.biometric.BiometricManager.from(context).canAuthenticate(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
                }
                ToggleRow(
                    stringResource(R.string.set_app_lock_title),
                    if (canLock)
                        stringResource(R.string.set_app_lock_subtitle_available)
                    else
                        stringResource(R.string.set_app_lock_subtitle_unavailable),
                    settings.appLockEnabled && canLock,
                ) { if (canLock) viewModel.setAppLockEnabled(it) }
                Text(
                    stringResource(R.string.set_app_lock_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }

            Section(stringResource(R.string.set_section_solo)) {
                NumberRow(
                    stringResource(R.string.set_network_difficulty),
                    if (settings.networkDifficulty > 0) "%.0f".format(settings.networkDifficulty) else "",
                ) { it.toDoubleOrNull()?.let { v -> viewModel.setNetworkDifficulty(v) } }
                ToggleRow(
                    stringResource(R.string.set_fetch_difficulty_title),
                    stringResource(R.string.set_fetch_difficulty_subtitle),
                    settings.difficultyAutoFetch,
                ) { viewModel.setDifficultyAutoFetch(it) }
            }

            Section(stringResource(R.string.set_section_units)) {
                ToggleRow(
                    stringResource(R.string.set_fahrenheit_title),
                    stringResource(R.string.set_fahrenheit_subtitle),
                    settings.useFahrenheit,
                ) {
                    viewModel.setUseFahrenheit(it)
                }
                NumberRow(stringResource(R.string.set_electricity_rate), settings.electricityRatePerKwh.toString()) {
                    it.toDoubleOrNull()?.let { v -> viewModel.setElectricityRate(v) }
                }
                NumberRow(stringResource(R.string.set_currency_code), settings.currencyCode) { viewModel.setCurrencyCode(it) }
                NumberRow(
                    stringResource(R.string.set_btc_price, settings.currencyCode),
                    if (settings.btcPrice > 0) "%.0f".format(settings.btcPrice) else "",
                ) { it.toDoubleOrNull()?.let { v -> viewModel.setBtcPrice(v) } }
                ToggleRow(
                    stringResource(R.string.set_fetch_btc_title),
                    stringResource(R.string.set_fetch_btc_subtitle),
                    settings.btcPriceAutoFetch,
                ) { viewModel.setBtcPriceAutoFetch(it) }
            }

            if (viewModel.selfUpdateEnabled) {
                Section(stringResource(R.string.set_section_update)) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val status by viewModel.updateStatus.collectAsStateWithLifecycle()
                    Text(stringResource(R.string.set_update_installed, viewModel.currentVersion), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.set_update_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                    when (val s = status) {
                        is SettingsViewModel.UpdateStatus.Downloading -> {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { s.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                stringResource(R.string.set_update_downloading, (s.progress * 100).toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = HiBrand.textSecondary,
                            )
                        }
                        is SettingsViewModel.UpdateStatus.Installing -> {
                            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                if (s.confirming) {
                                    stringResource(R.string.set_update_confirm_hint)
                                } else {
                                    stringResource(R.string.set_update_installing)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = HiBrand.textSecondary,
                            )
                        }
                        is SettingsViewModel.UpdateStatus.NeedsPermission -> {
                            Text(
                                stringResource(R.string.set_update_needs_permission),
                                style = MaterialTheme.typography.labelSmall,
                                color = HiBrand.accentAlt,
                            )
                            androidx.compose.material3.Button(onClick = {
                                viewModel.downloadAndInstall { intent ->
                                    runCatching { ctx.startActivity(intent) }
                                }
                            }) { Text(stringResource(R.string.set_update_download_install, s.info.latestVersion)) }
                        }
                        is SettingsViewModel.UpdateStatus.Available -> {
                            Text(
                                stringResource(R.string.set_update_available, s.info.latestVersion) +
                                    if (s.info.sizeBytes > 0) " (${s.info.sizeBytes / 1_000_000} MB)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HiBrand.accentAlt,
                            )
                            androidx.compose.material3.Button(onClick = {
                                viewModel.downloadAndInstall { intent ->
                                    runCatching { ctx.startActivity(intent) }
                                }
                            }) { Text(stringResource(R.string.set_update_download_install, s.info.latestVersion)) }
                        }
                        else -> {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.checkForUpdate() },
                                enabled = s !is SettingsViewModel.UpdateStatus.Checking,
                            ) {
                                Text(
                                    if (s is SettingsViewModel.UpdateStatus.Checking) stringResource(R.string.set_update_checking)
                                    else stringResource(R.string.set_update_check)
                                )
                            }
                            when (s) {
                                is SettingsViewModel.UpdateStatus.UpToDate ->
                                    Text(stringResource(R.string.set_update_up_to_date), style = MaterialTheme.typography.labelSmall, color = HiBrand.statusOnline)
                                is SettingsViewModel.UpdateStatus.Error ->
                                    Text(s.message, style = MaterialTheme.typography.labelSmall, color = HiBrand.statusOffline)
                                else -> {}
                            }
                        }
                    }
                }
            }

            Section(stringResource(R.string.set_section_data)) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val restoreMessage by viewModel.restoreMessage.collectAsStateWithLifecycle()
                val pendingEnc by viewModel.pendingEncryptedRestore.collectAsStateWithLifecycle()
                var backupPassPrompt by remember { mutableStateOf(false) }
                val restorePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> uri?.let { viewModel.restoreFrom(it) } }

                fun share(intent: android.content.Intent, title: String) =
                    context.launchChooser(intent, title)
                ActionRow(stringResource(R.string.set_export_report_7d)) {
                    viewModel.exportOpsReport(REPORT_WEEK_DAYS) { share(it, context.getString(R.string.set_share_fleet_report)) }
                }
                ActionRow(stringResource(R.string.set_export_report_30d)) {
                    viewModel.exportOpsReport(REPORT_MONTH_DAYS) { share(it, context.getString(R.string.set_share_fleet_report)) }
                }
                ActionRow(stringResource(R.string.set_export_csv_7d)) {
                    viewModel.exportFleetCsv { share(it, context.getString(R.string.set_share_export_csv)) }
                }
                ActionRow(stringResource(R.string.set_export_full_backup)) { backupPassPrompt = true }
                ActionRow(stringResource(R.string.set_restore_backup)) {
                    restorePicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                }
                val backupFolderPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    if (uri != null) {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                        viewModel.setAutoBackupFolder(uri.toString())
                    }
                }
                ActionRow(
                    if (settings.autoBackupFolderUri.isBlank()) stringResource(R.string.set_auto_backup_off)
                    else stringResource(R.string.set_auto_backup_on)
                ) { backupFolderPicker.launch(null) }
                if (settings.autoBackupFolderUri.isNotBlank()) {
                    if (settings.autoBackupLastMs > 0) {
                        Text(
                            stringResource(
                                R.string.set_last_auto_backup,
                                java.text.DateFormat.getDateTimeInstance(
                                    java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
                                ).format(java.util.Date(settings.autoBackupLastMs)),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = HiBrand.textSecondary,
                        )
                    }
                    ActionRow(stringResource(R.string.set_auto_backup_turn_off)) { viewModel.setAutoBackupFolder("") }
                }
                ActionRow(stringResource(R.string.set_export_diagnostics)) {
                    viewModel.exportDiagnostics(includeAddresses = false) { share(it, context.getString(R.string.set_share_export_diagnostics)) }
                }
                restoreMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.accentAlt)
                }
                Text(
                    stringResource(R.string.set_backup_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )

                if (backupPassPrompt) {
                    var pass by remember { mutableStateOf("") }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { backupPassPrompt = false },
                        title = { Text(stringResource(R.string.set_backup_pass_title)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(R.string.set_backup_pass_body),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                OutlinedTextField(
                                    value = pass,
                                    onValueChange = { pass = it },
                                    label = { Text(stringResource(R.string.set_passphrase_optional)) },
                                    singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                )
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                backupPassPrompt = false
                                viewModel.exportBackup(pass) { share(it, context.getString(R.string.set_share_export_backup)) }
                            }) { Text(stringResource(R.string.common_export)) }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { backupPassPrompt = false }) { Text(stringResource(R.string.common_cancel)) }
                        },
                    )
                }

                pendingEnc?.let { uri ->
                    var pass by remember { mutableStateOf("") }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { viewModel.pendingEncryptedRestore.value = null },
                        title = { Text(stringResource(R.string.set_encrypted_backup_title)) },
                        text = {
                            OutlinedTextField(
                                value = pass,
                                onValueChange = { pass = it },
                                label = { Text(stringResource(R.string.set_passphrase)) },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                viewModel.restoreFrom(uri, pass)
                            }) { Text(stringResource(R.string.set_restore)) }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { viewModel.pendingEncryptedRestore.value = null }) { Text(stringResource(R.string.common_cancel)) }
                        },
                    )
                }
            }

            Section(stringResource(R.string.set_section_demo)) {
                ToggleRow(
                    stringResource(R.string.set_demo_title),
                    stringResource(R.string.set_demo_subtitle),
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
                    stringResource(color.labelRes),
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
        Text(stringResource(R.string.set_pool_label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
        Text(stringResource(R.string.set_push_service), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            androidx.compose.material3.AssistChip(onClick = { open = true }, label = { Text(stringResource(current.labelRes)) })
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                hi3.hashkit.data.alerts.WebhookType.entries.forEach { t ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(t.labelRes)) },
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
                    context.getString(R.string.set_qr_not_bare_address),
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
                        .setPrompt(context.getString(R.string.set_scan_prompt))
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                )
            }) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.set_scan_qr_cd))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Preset refresh intervals from 5 seconds to 1 day (label resource to milliseconds). */
private val INTERVAL_PRESETS: List<Pair<Int, Long>> = listOf(
    R.string.vm_interval_5s to 5_000L, R.string.vm_interval_10s to 10_000L,
    R.string.vm_interval_15s to 15_000L, R.string.vm_interval_30s to 30_000L,
    R.string.vm_interval_1m to 60_000L, R.string.vm_interval_2m to 120_000L,
    R.string.vm_interval_5m to 300_000L, R.string.vm_interval_15m to 900_000L,
    R.string.vm_interval_30m to 1_800_000L, R.string.vm_interval_1h to 3_600_000L,
    R.string.vm_interval_6h to 21_600_000L, R.string.vm_interval_12h to 43_200_000L,
    R.string.vm_interval_1d to 86_400_000L,
)

@Composable
private fun intervalLabel(ms: Long): String =
    INTERVAL_PRESETS.firstOrNull { it.second == ms }?.first?.let { stringResource(it) }
        ?: when {
            ms % 86_400_000L == 0L -> stringResource(R.string.vm_interval_days, ms / 86_400_000L)
            ms % 3_600_000L == 0L -> stringResource(R.string.vm_interval_hours, ms / 3_600_000L)
            ms % 60_000L == 0L -> stringResource(R.string.vm_interval_mins, ms / 60_000L)
            else -> stringResource(R.string.vm_interval_secs, ms / 1000L)
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
                INTERVAL_PRESETS.forEach { (labelRes, ms) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(labelRes)) },
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

private const val REPORT_WEEK_DAYS = 7
private const val REPORT_MONTH_DAYS = 30
