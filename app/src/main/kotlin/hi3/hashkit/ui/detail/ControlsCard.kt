package hi3.hashkit.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hi3.hashkit.domain.adapter.TuneOptions
import hi3.hashkit.domain.model.Capability
import hi3.hashkit.domain.model.MinerCapabilities
import hi3.hashkit.ui.theme.HiBrand
import kotlin.math.roundToInt

/**
 * Safe controls. Everything is capability-gated, confirmed before execution, shows
 * current -> proposed values with risks, and tuning is limited to firmware-approved
 * option lists with a rollback path.
 */
@Composable
fun ControlsCard(
    capabilities: MinerCapabilities,
    tuneOptions: TuneOptions?,
    currentPool: Triple<String, Int, String>?,
    currentFrequencyMhz: Int?,
    currentVoltageMv: Int?,
    fanAutoNow: Boolean?,
    fanPercentNow: Int?,
    hasTuneToRollback: Boolean,
    busyAction: String?,
    lastActionMessage: String?,
    onReboot: () -> Unit,
    onSetPool: (url: String, port: Int, worker: String) -> Unit,
    onSetFanAuto: () -> Unit,
    onSetFanManual: (Int) -> Unit,
    onApplyTune: (freq: Int, volt: Int) -> Unit,
    onRollbackTune: () -> Unit,
) {
    var dialog by remember { mutableStateOf<ControlDialog?>(null) }

    Column {
        if (Capability.TELEMETRY in capabilities && capabilities.supported.size == 1) {
            Text(
                capabilities.unsupportedReasons.values.firstOrNull()
                    ?: "This device is monitoring-only.",
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.textSecondary,
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (Capability.REBOOT in capabilities) {
                OutlinedButton(
                    onClick = { dialog = ControlDialog.Reboot },
                    enabled = busyAction == null,
                ) { Text("Restart") }
            }
            if (Capability.SET_POOLS in capabilities) {
                OutlinedButton(
                    onClick = { dialog = ControlDialog.Pool },
                    enabled = busyAction == null,
                ) { Text("Pool") }
            }
            if (Capability.SET_FAN in capabilities) {
                OutlinedButton(
                    onClick = { dialog = ControlDialog.Fan },
                    enabled = busyAction == null,
                ) { Text("Fan") }
            }
            if (Capability.APPLY_APPROVED_TUNE in capabilities) {
                OutlinedButton(
                    onClick = { dialog = ControlDialog.Tune },
                    enabled = busyAction == null,
                ) { Text("Tune") }
            }
        }

        capabilities.unsupportedReasons[Capability.APPLY_APPROVED_TUNE]?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
        }

        if (busyAction != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Working: $busyAction…",
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.accentAlt,
            )
        }
        lastActionMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.accentAlt)
        }
    }

    when (dialog) {
        ControlDialog.Reboot -> ConfirmDialog(
            title = "Restart miner?",
            body = "Hashing stops for ~30–60 seconds while the miner reboots. Shares in flight may be lost. No settings are changed.",
            confirmLabel = "Restart",
            onConfirm = { dialog = null; onReboot() },
            onDismiss = { dialog = null },
        )
        ControlDialog.Pool -> PoolDialog(
            current = currentPool,
            onConfirm = { u, p, w -> dialog = null; onSetPool(u, p, w) },
            onDismiss = { dialog = null },
        )
        ControlDialog.Fan -> FanDialog(
            fanAutoNow = fanAutoNow,
            fanPercentNow = fanPercentNow,
            onAuto = { dialog = null; onSetFanAuto() },
            onManual = { dialog = null; onSetFanManual(it) },
            onDismiss = { dialog = null },
        )
        ControlDialog.Tune -> TuneDialog(
            options = tuneOptions,
            currentFrequencyMhz = currentFrequencyMhz,
            currentVoltageMv = currentVoltageMv,
            hasTuneToRollback = hasTuneToRollback,
            onApply = { f, v -> dialog = null; onApplyTune(f, v) },
            onRollback = { dialog = null; onRollbackTune() },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private enum class ControlDialog { Reboot, Pool, Fan, Tune }

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = HiBrand.statusDegraded) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PoolDialog(
    current: Triple<String, Int, String>?,
    onConfirm: (String, Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(current?.first ?: "") }
    var port by rememberSaveable { mutableStateOf(current?.second?.toString() ?: "3333") }
    var worker by rememberSaveable { mutableStateOf(current?.third ?: "") }
    val portInt = port.toIntOrNull()
    val valid = url.isNotBlank() && worker.isNotBlank() && portInt != null && portInt in 1..65535

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change primary pool") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                current?.let {
                    Text(
                        "Current: ${it.first}:${it.second}",
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                }
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Stratum URL") }, singleLine = true)
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, singleLine = true)
                OutlinedTextField(value = worker, onValueChange = { worker = it }, label = { Text("Worker / address") }, singleLine = true)
                Text(
                    "The stored pool password is preserved. A wrong URL stops mining until corrected. " +
                        "Older firmware may need a restart to pick up the change.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.statusDegraded,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim(), portInt ?: 0, worker.trim()) },
                enabled = valid,
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FanDialog(
    fanAutoNow: Boolean?,
    fanPercentNow: Int?,
    onAuto: () -> Unit,
    onManual: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var auto by rememberSaveable { mutableStateOf(fanAutoNow ?: true) }
    var percent by rememberSaveable { mutableStateOf((fanPercentNow ?: 70).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fan control") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Automatic (firmware-managed)", Modifier.padding(end = 12.dp))
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                if (!auto) {
                    Text("Manual speed: ${percent.roundToInt()}%")
                    Slider(value = percent, onValueChange = { percent = it }, valueRange = 20f..100f)
                    Text(
                        "Below ~35% a loaded miner can overheat. The firmware's overheat " +
                            "protection remains active either way.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.statusDegraded,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (auto) onAuto() else onManual(percent.roundToInt()) }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TuneDialog(
    options: TuneOptions?,
    currentFrequencyMhz: Int?,
    currentVoltageMv: Int?,
    hasTuneToRollback: Boolean,
    onApply: (Int, Int) -> Unit,
    onRollback: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (options == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Tuning unavailable") },
            text = {
                Text(
                    "This firmware does not publish its approved frequency/voltage lists " +
                        "(GET /api/system/asic), so the app will not offer tuning values for it.",
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        return
    }
    var freq by rememberSaveable {
        mutableStateOf(currentFrequencyMhz ?: options.defaultFrequencyMhz ?: options.frequencyOptionsMhz.first())
    }
    var volt by rememberSaveable {
        mutableStateOf(currentVoltageMv ?: options.defaultVoltageMv ?: options.voltageOptionsMv.first())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tune (firmware-approved values)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Current: ${currentFrequencyMhz ?: "?"} MHz / ${currentVoltageMv ?: "?"} mV" +
                        (options.defaultFrequencyMhz?.let { "  ·  Stock: $it MHz / ${options.defaultVoltageMv} mV" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
                Text("Frequency (MHz)", style = MaterialTheme.typography.labelMedium)
                OptionChips(options.frequencyOptionsMhz, freq, options.defaultFrequencyMhz) { freq = it }
                Text("Core voltage (mV)", style = MaterialTheme.typography.labelMedium)
                OptionChips(options.voltageOptionsMv, volt, options.defaultVoltageMv) { volt = it }
                Text(
                    "Values above stock raise heat and power draw and can shorten hardware " +
                        "life. The app polls faster after applying; watch temperatures and " +
                        "roll back if chip or VR temps climb.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.statusDegraded,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(freq, volt) }) { Text("Apply") }
        },
        dismissButton = {
            Row {
                if (hasTuneToRollback) {
                    TextButton(onClick = onRollback) { Text("Roll back") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun OptionChips(
    values: List<Int>,
    selected: Int,
    stock: Int?,
    onSelect: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        values.forEach { v ->
            FilterChip(
                selected = v == selected,
                onClick = { onSelect(v) },
                label = { Text(if (v == stock) "$v ✦" else "$v") },
            )
        }
    }
}
