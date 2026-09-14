package hi3.hashkit.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.R
import hi3.hashkit.core.Units
import hi3.hashkit.ui.components.Metric
import hi3.hashkit.ui.components.StatusBadge
import hi3.hashkit.ui.theme.HiBrand
import hi3.hashkit.ui.util.launchChooser
import hi3.hashkit.ui.util.openUrl
import hi3.hashkit.ui.util.shareFile
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinerDetailScreen(
    onBack: () -> Unit,
    onLogs: () -> Unit = {},
    onAutotune: () -> Unit = {},
    focusTelemetry: Boolean = false,
    viewModel: MinerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val miner = state.miner
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // When opened by a scan, scroll straight to the LIVE TELEMETRY section. Its offset is kept
    // current (charts above it change height as they render), and we scroll once the layout has
    // settled so we don't land above it on a stale offset.
    val scrollState = rememberScrollState()
    var telemetryY by remember { mutableStateOf(-1) }
    LaunchedEffect(focusTelemetry, miner?.id) {
        if (!focusTelemetry || miner == null) return@LaunchedEffect
        // Let the heavy sections (history/efficiency charts) lay out, then scroll to the settled
        // position; scroll again shortly after in case content is still growing.
        repeat(2) {
            kotlinx.coroutines.delay(400)
            if (telemetryY >= 0) scrollState.animateScrollTo(telemetryY)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(miner?.name ?: stringResource(R.string.det_title_miner), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { editing = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.det_cd_edit_miner))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.det_cd_remove_miner))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        containerColor = HiBrand.background,
    ) { padding ->
        if (miner == null) {
            Text(
                stringResource(R.string.det_miner_not_found),
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
                .verticalScroll(scrollState)
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
                            stringResource(
                                R.string.det_percent_of_expected,
                                String.format(java.util.Locale.US, "%.1f%%", it),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = HiBrand.textSecondary,
                        )
                    }
                }
                StatusBadge(miner.status)
            }

            SectionCard(stringResource(R.string.det_section_live_view)) {
                MinerVisual(
                    status = miner.status,
                    chipTempC = t?.chipTempC?.value,
                    fanRpm = t?.fans?.firstOrNull()?.rpm,
                    fanPercent = t?.fans?.firstOrNull()?.percent,
                )
                Text(
                    stringResource(R.string.det_live_view_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }

            SectionCard(stringResource(R.string.det_section_hashrate_history)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "1h" to 3_600_000L,
                        "24h" to 86_400_000L,
                        "7d" to 7 * 86_400_000L,
                        "30d" to 30 * 86_400_000L,
                    ).forEach { (label, ms) ->
                        androidx.compose.material3.FilterChip(
                            selected = viewModel.currentWindowMs == ms,
                            onClick = { viewModel.setWindow(ms) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                HashrateChart(state.history)
                Spacer(Modifier.height(4.dp))
                val stats = historyStats(state.history)
                Text(
                    lastReadingLabel(t?.timestamp) +
                        (stats?.let { stringResource(R.string.det_uptime_energy, it.first, it.second) } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                val anomalies = hi3.hashkit.domain.analysis.AnomalyDetector.analyze(state.history)
                anomalies.forEach { finding ->
                    Text(
                        "⚠ ${finding.message}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.statusDegraded,
                    )
                }
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.exportCsv { intent ->
                        context.launchChooser(intent, context.getString(R.string.det_export_csv_chooser))
                    }
                }) { Text(stringResource(R.string.det_export_csv)) }
            }

            if (efficiencyStats(state.history) != null) {
                SectionCard(stringResource(R.string.det_section_efficiency)) {
                    EfficiencyChart(state.history)
                    Spacer(Modifier.height(4.dp))
                    val e = efficiencyStats(state.history)!!
                    Text(
                        stringResource(
                            R.string.det_eff_stats,
                            "%.1f".format(e.first), "%.1f".format(e.second), "%.1f".format(e.third),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }

            (t?.perChain ?: emptyList()).takeIf { it.isNotEmpty() }?.let { chains ->
                SectionCard(stringResource(R.string.det_section_per_chip)) {
                    PerChipHealthCard(chains, state.settings.useFahrenheit)
                }
            }

            SectionCard(
                stringResource(R.string.det_section_live_telemetry),
                modifier = Modifier.onGloballyPositioned {
                    // boundsInParent here is the static content offset (does not move with scroll),
                    // so it's the value to scroll to. Kept current as the charts above settle.
                    telemetryY = it.boundsInParent().top.toInt()
                },
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    Metric(stringResource(R.string.det_metric_power), Units.formatPower(t?.powerW?.value), source = t?.powerW?.source)
                    Metric(stringResource(R.string.det_metric_efficiency), Units.formatEfficiency(t?.efficiencyJTh?.value), source = t?.efficiencyJTh?.source)
                    Metric(stringResource(R.string.det_metric_chip_temp), Units.formatTemp(t?.chipTempC?.value, state.settings.useFahrenheit))
                    Metric(stringResource(R.string.det_metric_vr_temp), Units.formatTemp(t?.vrTempC?.value, state.settings.useFahrenheit))
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    Metric(stringResource(R.string.det_metric_frequency), t?.frequencyMhz?.value?.let { "${it.toInt()} MHz" } ?: "—")
                    Metric(stringResource(R.string.det_metric_core_v), t?.coreVoltageMv?.value?.let { "${it.toInt()} mV" } ?: "—")
                    t?.fans?.forEach { fan ->
                        Metric(
                            stringResource(R.string.det_metric_fan_n, fan.index + 1),
                            fan.rpm?.let { "$it RPM" } ?: fan.percent?.let { "$it%" } ?: "—",
                        )
                    }
                    Metric(stringResource(R.string.det_metric_uptime), Units.formatUptime(t?.uptimeSeconds))
                }
            }

            if (!miner.isDemo || t?.powerW?.value != null) {
                SectionCard(stringResource(R.string.det_section_cost)) {
                    CostCard(
                        powerW = t?.powerW?.value,
                        hashrateGhs = t?.hashrateGhs?.value,
                        networkDifficulty = t?.networkDifficulty?.takeIf { it > 0 }
                            ?: state.settings.networkDifficulty.takeIf { it > 0 },
                        btcPrice = state.settings.btcPrice.takeIf { it > 0 },
                        ratePerKwh = state.settings.electricityRatePerKwh,
                        currency = state.settings.currencyCode,
                        purchasePrice = miner.purchasePrice,
                        onSaveRate = viewModel::setElectricityRate,
                    )
                }
            }

            SectionCard(stringResource(R.string.det_section_shares_pool)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    Metric(stringResource(R.string.det_metric_accepted), t?.sharesAccepted?.toString() ?: "—")
                    Metric(stringResource(R.string.det_metric_rejected), t?.sharesRejected?.toString() ?: "—")
                    Metric(stringResource(R.string.det_metric_best_diff), Units.formatDifficulty(t?.bestDifficulty))
                    Metric(stringResource(R.string.det_metric_session_best), Units.formatDifficulty(t?.bestSessionDifficulty))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.det_pool_prefix, t?.poolUrl?.let { "$it:${t.poolPort ?: "?"}" } ?: "—") +
                        if (t?.usingFallbackPool == true) stringResource(R.string.det_fallback_active) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }

            if (state.capabilities?.let { hi3.hashkit.domain.model.Capability.LOGS in it } == true) {
                SectionCard(stringResource(R.string.det_section_live_logs)) {
                    androidx.compose.material3.OutlinedButton(onClick = onLogs) {
                        Text(stringResource(R.string.det_open_live_logs))
                    }
                    Text(
                        stringResource(R.string.det_live_logs_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            } else if (state.capabilities != null) {
                SectionCard(stringResource(R.string.det_section_event_log)) {
                    androidx.compose.material3.OutlinedButton(onClick = onLogs) {
                        Text(stringResource(R.string.det_open_event_log))
                    }
                    Text(
                        stringResource(R.string.det_event_log_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }

            SectionCard(stringResource(R.string.det_section_maintenance)) {
                val notes by viewModel.maintenanceNotes.collectAsStateWithLifecycle()
                var noteText by remember { mutableStateOf("") }
                var pendingPhoto by remember { mutableStateOf<android.net.Uri?>(null) }
                val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                ) { uri -> pendingPhoto = uri }
                // In-app capture: the camera writes to a staged cache file; "Add note" then
                // copies it into permanent app-private storage like any picked photo.
                var cameraTarget by remember { mutableStateOf<android.net.Uri?>(null) }
                val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
                ) { saved -> if (saved) pendingPhoto = cameraTarget }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.det_add_note_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.TextButton(
                        enabled = noteText.isNotBlank() || pendingPhoto != null,
                        onClick = {
                            viewModel.addMaintenanceNote(noteText, pendingPhoto)
                            noteText = ""; pendingPhoto = null
                        },
                    ) { Text(stringResource(R.string.det_add_note)) }
                    androidx.compose.material3.TextButton(onClick = {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            )
                        )
                    }) { Text(stringResource(R.string.det_gallery)) }
                    androidx.compose.material3.TextButton(onClick = {
                        runCatching {
                            val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.files",
                                java.io.File(dir, "capture_${System.currentTimeMillis()}.jpg"),
                            )
                            cameraTarget = uri
                            cameraLauncher.launch(uri)
                        }
                    }) { Text(stringResource(R.string.det_camera)) }
                    if (pendingPhoto != null) {
                        Text("📷", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (notes.isEmpty()) {
                    Text(
                        stringResource(R.string.det_no_notes),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                } else {
                    notes.forEach { note ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f)) {
                                if (note.text.isNotBlank()) {
                                    Text(note.text, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(
                                    java.text.DateFormat.getDateTimeInstance(
                                        java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
                                    ).format(java.util.Date(note.atEpochMs)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HiBrand.textSecondary,
                                )
                                note.photoPath?.let { NotePhoto(it) }
                            }
                            androidx.compose.material3.IconButton(
                                onClick = { viewModel.deleteMaintenanceNote(note) },
                            ) {
                                Icon(
                                    androidx.compose.material.icons.Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.det_cd_delete_note),
                                    tint = HiBrand.statusOffline,
                                )
                            }
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.det_section_identity)) {
                InfoRow(stringResource(R.string.det_id_model), miner.identity.model)
                InfoRow(stringResource(R.string.det_id_asic), miner.identity.asicModel)
                InfoRow(stringResource(R.string.det_id_firmware), listOfNotNull(miner.identity.firmwareFamily, miner.identity.firmwareVersion).joinToString(" "))
                InfoRow(stringResource(R.string.det_id_mac), miner.identity.macAddress)
                InfoRow(stringResource(R.string.det_id_serial), miner.identity.serialNumber)
                InfoRow(
                    stringResource(R.string.det_id_address), "${miner.host}:${miner.port}",
                    // Opens the machine's own web UI; the web interface lives on port 80
                    // for every supported family even when the API port differs (e.g. 4028).
                    onClick = { context.openUrl("http://${miner.host}") },
                )
            }

            state.healthScore?.let { health ->
                SectionCard(stringResource(R.string.det_section_health)) {
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

            // VNish / stock Bitmain controls authenticate with the miner's web password;
            // NerdQAxe optionally protects writes with TOTP (the secret from its enrollment QR).
            val fw = miner.identity.firmwareFamily?.lowercase() ?: ""
            val model = miner.identity.model ?: ""
            val isNerdQaxe = model.contains("NerdQAxe", ignoreCase = true) ||
                model.contains("NerdAxe", ignoreCase = true)
            val needsLogin = "vnish" in fw || "bitmain" in fw || isNerdQaxe
            val loginLabel = when {
                isNerdQaxe -> stringResource(R.string.det_login_label_totp)
                "bitmain" in fw -> stringResource(R.string.det_login_label_bitmain)
                else -> stringResource(R.string.det_login_label_vnish)
            }
            if (needsLogin) {
                SectionCard(stringResource(R.string.det_section_login)) {
                    val credSet by viewModel.credentialSet.collectAsStateWithLifecycle()
                    var pw by remember { mutableStateOf("") }
                    Text(
                        when {
                            credSet -> stringResource(R.string.det_login_saved_hint)
                            isNerdQaxe -> stringResource(R.string.det_login_totp_hint)
                            else -> stringResource(R.string.det_login_password_hint)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = pw,
                        onValueChange = { pw = it },
                        label = { Text(loginLabel) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Button(onClick = { viewModel.setCredential(pw); pw = "" }) { Text(stringResource(R.string.common_save)) }
                        if (credSet) {
                            androidx.compose.material3.OutlinedButton(onClick = { viewModel.setCredential("") }) { Text(stringResource(R.string.det_clear)) }
                        }
                    }
                }
            }

            state.capabilities?.let { caps ->
                SectionCard(stringResource(R.string.det_section_controls)) {
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
                        onLocate = viewModel::locate,
                        onSetPool = viewModel::setPool,
                        onSetFanAuto = viewModel::setFanAuto,
                        onSetFanManual = viewModel::setFanManual,
                        onApplyTune = viewModel::applyTune,
                        onRollbackTune = viewModel::rollbackTune,
                        onPause = viewModel::pauseHashing,
                        onResume = viewModel::resumeHashing,
                        displayNow = t?.let {
                            hi3.hashkit.domain.adapter.DisplayControl(
                                it.displayRotationDegrees, it.displayInverted, it.displayTimeoutMinutes,
                            )
                        },
                        onSetDisplay = viewModel::setDisplay,
                    )
                    if (state.tuneOptions != null) {
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = onAutotune,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.det_autotuner)) }
                    }
                }
            }

            SectionCard(stringResource(R.string.det_section_plug)) {
                val plug by viewModel.plug.collectAsStateWithLifecycle()
                SmartPlugCard(
                    plug = plug,
                    onSave = viewModel::saveSmartPlug,
                    onTest = viewModel::testPlug,
                )
            }

            if (state.alerts.isNotEmpty()) {
                SectionCard(stringResource(R.string.det_section_alerts)) {
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
                                if (alert.resolvedAtEpochMs == null) stringResource(R.string.det_alert_active)
                                else stringResource(R.string.det_alert_resolved),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (alert.resolvedAtEpochMs == null) HiBrand.statusDegraded
                                else HiBrand.statusOnline,
                            )
                        }
                    }
                }
            }

            SectionCard(
                title = stringResource(R.string.det_section_raw) +
                    if (state.showRaw) "" else stringResource(R.string.det_tap_to_show),
                onClick = { viewModel.toggleRaw() },
            ) {
                if (state.showRaw) {
                    Text(
                        state.rawResponse ?: stringResource(R.string.det_no_raw),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = HiBrand.textSecondary,
                    )
                    Text(
                        stringResource(R.string.det_raw_redacted),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.statusDegraded,
                    )
                }
            }
        }
    }

    if (editing && miner != null) {
        val farms by viewModel.farms.collectAsStateWithLifecycle()
        val currentFarmId by viewModel.farmId.collectAsStateWithLifecycle()
        EditMinerDialog(
            miner = miner,
            farms = farms,
            currentFarmId = currentFarmId,
            currency = state.settings.currencyCode,
            onSave = { name, group, location, notes, tags, expected, overrides, farmId ->
                viewModel.saveMeta(name, group, location, notes, tags, expected, overrides, farmId)
                editing = false
            },
            onSavePurchasePrice = viewModel::setPurchasePrice,
            onDismiss = { editing = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.det_remove_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.det_remove_body,
                        miner?.name ?: stringResource(R.string.det_this_miner),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteMiner(onDeleted = onBack)
                }) { Text(stringResource(R.string.det_remove), color = HiBrand.statusOffline) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/** A downsampled thumbnail of a maintenance-note photo, decoded off-path and cached.
 *  Tap to open the photo full-size (tap again to close). */
@Composable
private fun NotePhoto(path: String) {
    var viewing by remember(path) { mutableStateOf(false) }
    val bitmap = androidx.compose.runtime.remember(path) {
        runCatching {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
            android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = stringResource(R.string.det_cd_note_photo),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .padding(top = 6.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { viewing = true },
        )
    }
    if (viewing) {
        // Full-resolution view, capped near screen size so a large camera JPEG can't OOM.
        val full = androidx.compose.runtime.remember(path) {
            runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 2048 || bounds.outHeight / (sample * 2) >= 2048) {
                    sample *= 2
                }
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
            }.getOrNull()
        }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewing = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            // Pinch to zoom, drag to pan; a plain tap (anywhere) still closes the viewer.
            var scale by remember { mutableStateOf(1f) }
            var pan by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            val transformState = rememberTransformableState { zoom, offset, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                pan = if (scale > 1f) pan + offset else androidx.compose.ui.geometry.Offset.Zero
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { viewing = false },
                contentAlignment = Alignment.Center,
            ) {
                if (full != null) {
                    androidx.compose.foundation.Image(
                        bitmap = full,
                        contentDescription = stringResource(R.string.det_cd_photo),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .graphicsLayer {
                                scaleX = scale; scaleY = scale
                                translationX = pan.x; translationY = pan.y
                            }
                            .transformable(transformState)
                            // Tap closes; double-tap toggles 1x/3x (gallery convention).
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { viewing = false },
                                    onDoubleTap = {
                                        if (scale > 1f) {
                                            scale = 1f
                                            pan = androidx.compose.ui.geometry.Offset.Zero
                                        } else {
                                            scale = 3f
                                        }
                                    },
                                )
                            },
                    )
                    androidx.compose.material3.IconButton(
                        onClick = { context.shareFile(java.io.File(path), "image/jpeg", context.getString(R.string.det_share_photo)) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Filled.Share,
                            contentDescription = stringResource(R.string.det_share_photo),
                            tint = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                } else {
                    Text(stringResource(R.string.det_photo_missing), color = HiBrand.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(14.dp),
        onClick = onClick ?: {},
        enabled = true,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?, onClick: (() -> Unit)? = null) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
            .padding(vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary)
        Text(
            value?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = if (onClick != null) HiBrand.accent else androidx.compose.ui.graphics.Color.Unspecified,
            textDecoration = if (onClick != null) {
                androidx.compose.ui.text.style.TextDecoration.Underline
            } else null,
        )
    }
}

/** (uptime %, energy string) over the loaded history window, or null when too sparse. */
private fun historyStats(history: List<hi3.hashkit.domain.model.MinerTelemetry>): Pair<Int, String>? {
    if (history.size < 3) return null
    val online = history.count { it.status == hi3.hashkit.domain.model.MinerStatus.ONLINE }
    val uptimePct = (online * 100.0 / history.size).toInt()
    // Energy: integrate power over inter-sample gaps, capped at 5 min to avoid
    // fabricating consumption across app-closed periods.
    var wh = 0.0
    for (i in 1 until history.size) {
        val dtH = (history[i].timestamp.toEpochMilli() - history[i - 1].timestamp.toEpochMilli())
            .coerceAtMost(300_000L) / 3_600_000.0
        history[i].powerW.value?.let { wh += it * dtH }
    }
    val energy = if (wh >= 1000) String.format(java.util.Locale.US, "%.2f kWh", wh / 1000)
    else String.format(java.util.Locale.US, "%.0f Wh", wh)
    return uptimePct to energy
}

@Suppress("LongMethod") // a declarative form: one field per editable miner property
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditMinerDialog(
    miner: hi3.hashkit.domain.model.Miner,
    farms: List<hi3.hashkit.data.db.FarmEntity>,
    currentFarmId: Long?,
    currency: String,
    onSave: (
        String, String?, String?, String?, String, Double?,
        hi3.hashkit.domain.alerts.AlertOverrides, Long?,
    ) -> Unit,
    onSavePurchasePrice: (Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(miner.name) }
    var paid by remember {
        mutableStateOf(miner.purchasePrice?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "")
    }
    var group by remember { mutableStateOf(miner.group ?: "") }
    var location by remember { mutableStateOf(miner.location ?: "") }
    // The farm assignment arrives async (the flow's first frame is null), so follow it
    // until the user actually taps a chip — otherwise saving before the first emission
    // would silently clear the miner's farm.
    var farmTouched by remember { mutableStateOf(false) }
    var farmId by remember { mutableStateOf(currentFarmId) }
    androidx.compose.runtime.LaunchedEffect(currentFarmId) { if (!farmTouched) farmId = currentFarmId }
    var notes by remember { mutableStateOf(miner.notes ?: "") }
    var tags by remember { mutableStateOf(miner.tags.joinToString(", ")) }
    var expected by remember { mutableStateOf(miner.expectedHashrateGhs?.toString() ?: "") }
    val ov = miner.alertOverrides
    var ovHash by remember { mutableStateOf(ov.hashrateBelowPercent?.toString() ?: "") }
    var ovChip by remember { mutableStateOf(ov.chipTempC?.toString() ?: "") }
    var ovVr by remember { mutableStateOf(ov.vrTempC?.toString() ?: "") }
    var ovReject by remember { mutableStateOf(ov.rejectRatePercent?.toString() ?: "") }
    var muted by remember { mutableStateOf(ov.muted) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.det_edit_miner_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.det_name)) }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text(stringResource(R.string.det_group)) }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text(stringResource(R.string.det_location)) }, singleLine = true)
                if (farms.isNotEmpty()) {
                    Text(stringResource(R.string.det_farm_header), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        androidx.compose.material3.FilterChip(
                            selected = farmId == null,
                            onClick = {
                                farmTouched = true
                                farmId = null
                            },
                            label = { Text(stringResource(R.string.common_none)) },
                        )
                        farms.forEach { farm ->
                            androidx.compose.material3.FilterChip(
                                selected = farmId == farm.id,
                                onClick = {
                                    farmTouched = true
                                    farmId = farm.id
                                },
                                label = { Text(farm.name) },
                            )
                        }
                    }
                }
                androidx.compose.material3.OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text(stringResource(R.string.det_tags)) }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(
                    value = expected,
                    onValueChange = { expected = it },
                    label = { Text(stringResource(R.string.det_expected_hashrate)) },
                    singleLine = true,
                )
                androidx.compose.material3.OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.det_notes)) })
                androidx.compose.material3.OutlinedTextField(
                    value = paid,
                    onValueChange = { paid = it },
                    label = { Text(stringResource(R.string.det_paid_label, currency)) },
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.det_alert_overrides_header),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                androidx.compose.material3.OutlinedTextField(value = ovHash, onValueChange = { ovHash = it }, label = { Text(stringResource(R.string.det_ov_hashrate)) }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = ovChip, onValueChange = { ovChip = it }, label = { Text(stringResource(R.string.det_ov_chip)) }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = ovVr, onValueChange = { ovVr = it }, label = { Text(stringResource(R.string.det_ov_vr)) }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = ovReject, onValueChange = { ovReject = it }, label = { Text(stringResource(R.string.det_ov_reject)) }, singleLine = true)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.det_mute), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.det_mute_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = HiBrand.textSecondary,
                        )
                    }
                    androidx.compose.material3.Switch(checked = muted, onCheckedChange = { muted = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSavePurchasePrice(paid.trim().replace(',', '.').toDoubleOrNull())
                    onSave(
                        name.trim(), group, location, notes, tags, expected.toDoubleOrNull(),
                        hi3.hashkit.domain.alerts.AlertOverrides(
                            hashrateBelowPercent = ovHash.toDoubleOrNull(),
                            chipTempC = ovChip.toDoubleOrNull(),
                            vrTempC = ovVr.toDoubleOrNull(),
                            rejectRatePercent = ovReject.toDoubleOrNull(),
                            muted = muted,
                        ),
                        farmId,
                    )
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun lastReadingLabel(ts: Instant?): String {
    if (ts == null) return stringResource(R.string.det_no_reading_yet)
    val secs = Duration.between(ts, Instant.now()).seconds
    return if (secs < 90) stringResource(R.string.det_last_reading_seconds, secs)
    else stringResource(R.string.det_last_reading_minutes, secs / 60)
}

@Composable
private fun SmartPlugCard(
    plug: hi3.hashkit.ui.detail.PlugConfig,
    onSave: (hi3.hashkit.integrations.plug.PlugType?, String, String, String, Double?) -> Unit,
    onTest: (Boolean) -> Unit,
) {
    val types = hi3.hashkit.integrations.plug.PlugType.entries
    var type by remember(plug.type) { mutableStateOf(plug.type) }
    var host by remember(plug.host) { mutableStateOf(plug.host) }
    var onUrl by remember(plug.onUrl) { mutableStateOf(plug.onUrl) }
    var offUrl by remember(plug.offUrl) { mutableStateOf(plug.offUrl) }
    var cutoff by remember(plug.cutoffC) { mutableStateOf(plug.cutoffC?.toInt()?.toString() ?: "") }
    var menu by remember { mutableStateOf(false) }

    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.det_plug_hint),
            style = MaterialTheme.typography.labelSmall,
            color = HiBrand.textSecondary,
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.det_plug), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            androidx.compose.foundation.layout.Box {
                androidx.compose.material3.AssistChip(
                    onClick = { menu = true },
                    label = { Text(type?.label ?: stringResource(R.string.common_disabled)) },
                )
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.common_disabled)) }, onClick = { menu = false; type = null })
                    types.forEach { t ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(t.labelRes)) }, onClick = { menu = false; type = t })
                    }
                }
            }
        }
        when (type) {
            null -> Unit
            hi3.hashkit.integrations.plug.PlugType.WEBHOOK -> {
                androidx.compose.material3.OutlinedTextField(value = offUrl, onValueChange = { offUrl = it }, label = { Text(stringResource(R.string.det_off_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.OutlinedTextField(value = onUrl, onValueChange = { onUrl = it }, label = { Text(stringResource(R.string.det_on_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            else -> androidx.compose.material3.OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text(stringResource(R.string.det_plug_host)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (type != null) {
            androidx.compose.material3.OutlinedTextField(value = cutoff, onValueChange = { cutoff = it }, label = { Text(stringResource(R.string.det_plug_cutoff)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(onClick = { onSave(type, host, onUrl, offUrl, cutoff.toDoubleOrNull()) }) { Text(stringResource(R.string.common_save)) }
            if (type != null) {
                androidx.compose.material3.OutlinedButton(onClick = { onTest(false) }) { Text(stringResource(R.string.det_test_off)) }
                androidx.compose.material3.OutlinedButton(onClick = { onTest(true) }) { Text(stringResource(R.string.det_test_on)) }
            }
        }
    }
}
