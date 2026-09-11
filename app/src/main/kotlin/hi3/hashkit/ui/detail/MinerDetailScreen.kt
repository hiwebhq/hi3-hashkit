package hi3.hashkit.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
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
                title = { Text(miner?.name ?: "Miner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { editing = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit miner")
                    }
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
                            String.format(java.util.Locale.US, "%.1f%% of expected", it),
                            style = MaterialTheme.typography.bodySmall,
                            color = HiBrand.textSecondary,
                        )
                    }
                }
                StatusBadge(miner.status)
            }

            SectionCard("LIVE VIEW") {
                MinerVisual(
                    status = miner.status,
                    chipTempC = t?.chipTempC?.value,
                    fanRpm = t?.fans?.firstOrNull()?.rpm,
                    fanPercent = t?.fans?.firstOrNull()?.percent,
                )
                Text(
                    "Stylized live render: fan spins with reported RPM, chips glow by " +
                        "temperature, LED shows status.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }

            SectionCard("HASHRATE HISTORY") {
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
                        (stats?.let { "  ·  uptime ${it.first}%  ·  ~${it.second} energy" } ?: ""),
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
                        context.startActivity(
                            android.content.Intent.createChooser(intent, "Export telemetry CSV")
                        )
                    }
                }) { Text("Export CSV") }
            }

            if (efficiencyStats(state.history) != null) {
                SectionCard("EFFICIENCY (J/TH)") {
                    EfficiencyChart(state.history)
                    Spacer(Modifier.height(4.dp))
                    val e = efficiencyStats(state.history)!!
                    Text(
                        "min %.1f  ·  now %.1f  ·  max %.1f J/TH   (lower is better)".format(e.first, e.second, e.third),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }

            (t?.perChain ?: emptyList()).takeIf { it.isNotEmpty() }?.let { chains ->
                SectionCard("PER-CHIP HEALTH") {
                    PerChipHealthCard(chains, state.settings.useFahrenheit)
                }
            }

            SectionCard(
                "LIVE TELEMETRY",
                modifier = Modifier.onGloballyPositioned {
                    telemetryY = it.boundsInParent().top.toInt()
                },
            ) {
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

            if (state.capabilities?.let { hi3.hashkit.domain.model.Capability.LOGS in it } == true) {
                SectionCard("LIVE LOGS") {
                    androidx.compose.material3.OutlinedButton(onClick = onLogs) {
                        Text("Open live log stream")
                    }
                    Text(
                        "Streams firmware logs over the miner's WebSocket while open. " +
                            "Wallet addresses are redacted.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }

            SectionCard("MAINTENANCE LOG") {
                val notes by viewModel.maintenanceNotes.collectAsStateWithLifecycle()
                var noteText by remember { mutableStateOf("") }
                var pendingPhoto by remember { mutableStateOf<android.net.Uri?>(null) }
                val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                ) { uri -> pendingPhoto = uri }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Add a note (e.g. \"repasted\", \"replaced fan 2\")") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.TextButton(
                        enabled = noteText.isNotBlank() || pendingPhoto != null,
                        onClick = {
                            viewModel.addMaintenanceNote(noteText, pendingPhoto)
                            noteText = ""; pendingPhoto = null
                        },
                    ) { Text("Add note") }
                    androidx.compose.material3.TextButton(onClick = {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            )
                        )
                    }) { Text(if (pendingPhoto != null) "📷 attached" else "Attach photo") }
                }
                if (notes.isEmpty()) {
                    Text(
                        "No maintenance notes yet. Log repastes, fan swaps, cleanings — they " +
                            "stay on this device and are included in a full backup.",
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
                                    contentDescription = "Delete note",
                                    tint = HiBrand.statusOffline,
                                )
                            }
                        }
                    }
                }
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

            // VNish / stock Bitmain controls authenticate with the miner's web password.
            val fw = miner.identity.firmwareFamily?.lowercase() ?: ""
            val needsLogin = "vnish" in fw || "bitmain" in fw
            val loginLabel = if ("bitmain" in fw) "Root web password" else "VNish web password"
            if (needsLogin) {
                SectionCard("MINER LOGIN (FOR CONTROLS)") {
                    val credSet by viewModel.credentialSet.collectAsStateWithLifecycle()
                    var pw by remember { mutableStateOf("") }
                    Text(
                        if (credSet) "A web password is saved (encrypted). Enter a new one to replace it, or clear it."
                        else "Enter the miner's web password to enable controls. Stored encrypted on this device only.",
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
                        androidx.compose.material3.Button(onClick = { viewModel.setCredential(pw); pw = "" }) { Text("Save") }
                        if (credSet) {
                            androidx.compose.material3.OutlinedButton(onClick = { viewModel.setCredential("") }) { Text("Clear") }
                        }
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
                        onPause = viewModel::pauseHashing,
                        onResume = viewModel::resumeHashing,
                    )
                    if (state.tuneOptions != null) {
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = onAutotune,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Efficiency autotuner (sweep J/TH)") }
                    }
                }
            }

            SectionCard("SAFETY CUTOFF (SMART PLUG)") {
                val plug by viewModel.plug.collectAsStateWithLifecycle()
                SmartPlugCard(
                    plug = plug,
                    onSave = viewModel::saveSmartPlug,
                    onTest = viewModel::testPlug,
                )
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

    if (editing && miner != null) {
        EditMinerDialog(
            miner = miner,
            onSave = { name, group, location, notes, tags, expected, overrides ->
                viewModel.saveMeta(name, group, location, notes, tags, expected, overrides)
                editing = false
            },
            onDismiss = { editing = false },
        )
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

/** A downsampled thumbnail of a maintenance-note photo, decoded off-path and cached. */
@Composable
private fun NotePhoto(path: String) {
    val bitmap = androidx.compose.runtime.remember(path) {
        runCatching {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
            android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = "Maintenance photo",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .padding(top = 6.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
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

@Composable
private fun EditMinerDialog(
    miner: hi3.hashkit.domain.model.Miner,
    onSave: (String, String?, String?, String?, String, Double?, hi3.hashkit.domain.alerts.AlertOverrides) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(miner.name) }
    var group by remember { mutableStateOf(miner.group ?: "") }
    var location by remember { mutableStateOf(miner.location ?: "") }
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
        title = { Text("Edit miner") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("Group") }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location (room/rack/shelf)") }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags (comma-separated)") }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(
                    value = expected,
                    onValueChange = { expected = it },
                    label = { Text("Expected hashrate (GH/s, blank = device-reported)") },
                    singleLine = true,
                )
                androidx.compose.material3.OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
                Text(
                    "ALERT OVERRIDES (blank = global default)",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
                androidx.compose.material3.OutlinedTextField(value = ovHash, onValueChange = { ovHash = it }, label = { Text("Hashrate alert below (%)") }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = ovChip, onValueChange = { ovChip = it }, label = { Text("Chip temp alert (°C)") }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = ovVr, onValueChange = { ovVr = it }, label = { Text("VR temp alert (°C)") }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(value = ovReject, onValueChange = { ovReject = it }, label = { Text("Reject-rate alert (%)") }, singleLine = true)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mute alerts for this miner", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Still polled and charted; raises no alerts.",
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
                    onSave(
                        name.trim(), group, location, notes, tags, expected.toDoubleOrNull(),
                        hi3.hashkit.domain.alerts.AlertOverrides(
                            hashrateBelowPercent = ovHash.toDoubleOrNull(),
                            chipTempC = ovChip.toDoubleOrNull(),
                            vrTempC = ovVr.toDoubleOrNull(),
                            rejectRatePercent = ovReject.toDoubleOrNull(),
                            muted = muted,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun lastReadingLabel(ts: Instant?): String {
    if (ts == null) return "No successful reading yet"
    val secs = Duration.between(ts, Instant.now()).seconds
    return if (secs < 90) "Last reading ${secs}s ago" else "Last reading ${secs / 60}m ago — stale"
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
            "Cut power via a local smart plug when chip temp reaches the limit. On is always " +
                "manual — the app never auto-restores power. Local addresses only.",
            style = MaterialTheme.typography.labelSmall,
            color = HiBrand.textSecondary,
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Plug", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            androidx.compose.foundation.layout.Box {
                androidx.compose.material3.AssistChip(
                    onClick = { menu = true },
                    label = { Text(type?.label ?: "Disabled") },
                )
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text("Disabled") }, onClick = { menu = false; type = null })
                    types.forEach { t ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(t.label) }, onClick = { menu = false; type = t })
                    }
                }
            }
        }
        when (type) {
            null -> Unit
            hi3.hashkit.integrations.plug.PlugType.WEBHOOK -> {
                androidx.compose.material3.OutlinedTextField(value = offUrl, onValueChange = { offUrl = it }, label = { Text("OFF URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.OutlinedTextField(value = onUrl, onValueChange = { onUrl = it }, label = { Text("ON URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            else -> androidx.compose.material3.OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Plug IP / host") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (type != null) {
            androidx.compose.material3.OutlinedTextField(value = cutoff, onValueChange = { cutoff = it }, label = { Text("Cut power at chip temp (°C)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(onClick = { onSave(type, host, onUrl, offUrl, cutoff.toDoubleOrNull()) }) { Text("Save") }
            if (type != null) {
                androidx.compose.material3.OutlinedButton(onClick = { onTest(false) }) { Text("Test off") }
                androidx.compose.material3.OutlinedButton(onClick = { onTest(true) }) { Text("Test on") }
            }
        }
    }
}
