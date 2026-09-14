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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hi3.hashkit.R
import hi3.hashkit.domain.adapter.DisplayControl
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
@Suppress("LongMethod", "CyclomaticComplexMethod") // one capability-gated row/dialog per control
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
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onLocate: (Boolean) -> Unit = {},
    displayNow: DisplayControl? = null,
    onSetDisplay: (DisplayControl) -> Unit = {},
) {
    var dialog by remember { mutableStateOf<ControlDialog?>(null) }
    var blinking by remember { mutableStateOf(false) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun buzz() = haptics.performHapticFeedback(
        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
    )

    Column {
        if (Capability.TELEMETRY in capabilities && capabilities.supported.size == 1) {
            Text(
                capabilities.unsupportedReasons.values.firstOrNull()
                    ?: stringResource(R.string.det_monitoring_only),
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
                ) { Text(stringResource(R.string.det_restart)) }
            }
            if (Capability.LOCATE in capabilities) {
                OutlinedButton(
                    onClick = {
                        blinking = !blinking
                        onLocate(blinking)
                    },
                    enabled = busyAction == null,
                ) { Text(if (blinking) stringResource(R.string.det_stop_blink) else stringResource(R.string.det_blink_led)) }
            }
            if (Capability.SET_POOLS in capabilities) {
                OutlinedButton(
                    onClick = { dialog = ControlDialog.Pool },
                    enabled = busyAction == null,
                ) { Text(stringResource(R.string.det_pool)) }
            }
            if (Capability.SET_FAN in capabilities) {
                OutlinedButton(
                    onClick = { dialog = ControlDialog.Fan },
                    enabled = busyAction == null,
                ) { Text(stringResource(R.string.det_fan)) }
            }
            if (Capability.APPLY_APPROVED_TUNE in capabilities) {
                OutlinedButton(
                    onClick = { dialog = ControlDialog.Tune },
                    enabled = busyAction == null,
                ) { Text(stringResource(R.string.det_tune)) }
            }
            if (Capability.POWER_CONTROL in capabilities) {
                OutlinedButton(
                    onClick = { dialog = ControlDialog.Power },
                    enabled = busyAction == null,
                ) { Text(stringResource(R.string.det_power)) }
            }
            if (Capability.SET_DISPLAY in capabilities) {
                OutlinedButton(
                    onClick = { dialog = ControlDialog.Display },
                    enabled = busyAction == null,
                ) { Text(stringResource(R.string.det_display)) }
            }
        }

        capabilities.unsupportedReasons[Capability.APPLY_APPROVED_TUNE]?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
        }

        if (busyAction != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.det_working, busyAction),
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
            title = stringResource(R.string.det_restart_title),
            body = stringResource(R.string.det_restart_body),
            confirmLabel = stringResource(R.string.det_restart),
            onConfirm = { buzz(); dialog = null; onReboot() },
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
            onApply = { f, v -> buzz(); dialog = null; onApplyTune(f, v) },
            onRollback = { buzz(); dialog = null; onRollbackTune() },
            onDismiss = { dialog = null },
        )
        ControlDialog.Power -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.det_power_title)) },
            text = {
                Text(
                    stringResource(R.string.det_power_body),
                )
            },
            confirmButton = {
                TextButton(onClick = { dialog = null; onPause() }) {
                    Text(stringResource(R.string.det_pause), color = HiBrand.statusDegraded)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { dialog = null; onResume() }) { Text(stringResource(R.string.det_resume)) }
                    TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_cancel)) }
                }
            },
        )
        ControlDialog.Display -> DisplayDialog(
            current = displayNow,
            onApply = { dialog = null; onSetDisplay(it) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private enum class ControlDialog { Reboot, Pool, Fan, Tune, Power, Display }

/** Timeout choices mirror the AxeOS web UI (minutes; -1 always on, 0 always off). */
@Suppress("MagicNumber") // the choice list is the data
private val DISPLAY_TIMEOUT_CHOICES = listOf(
    DisplayControl.TIMEOUT_ALWAYS_ON, DisplayControl.TIMEOUT_ALWAYS_OFF, 1, 5, 15, 30, 60, 240,
)
private const val MINUTES_PER_HOUR = 60

@Composable
private fun displayTimeoutLabel(minutes: Int): String = when {
    minutes == DisplayControl.TIMEOUT_ALWAYS_ON -> stringResource(R.string.det_display_always_on)
    minutes == DisplayControl.TIMEOUT_ALWAYS_OFF -> stringResource(R.string.det_display_always_off)
    minutes % MINUTES_PER_HOUR == 0 -> stringResource(R.string.det_display_hours, minutes / MINUTES_PER_HOUR)
    else -> stringResource(R.string.det_display_minutes, minutes)
}

@Composable
private fun DisplayDialog(
    current: DisplayControl?,
    onApply: (DisplayControl) -> Unit,
    onDismiss: () -> Unit,
) {
    var rotation by rememberSaveable { mutableStateOf(current?.rotationDegrees ?: 0) }
    var inverted by rememberSaveable { mutableStateOf(current?.inverted ?: false) }
    var timeout by rememberSaveable {
        mutableStateOf(current?.timeoutMinutes ?: DisplayControl.TIMEOUT_ALWAYS_ON)
    }
    // A timeout the firmware reports but we don't list (custom value) stays selectable.
    val timeoutChoices = (DISPLAY_TIMEOUT_CHOICES + timeout).distinct().sorted()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.det_display_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.det_display_rotation), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DisplayControl.ROTATIONS.forEach { deg ->
                        FilterChip(
                            selected = rotation == deg,
                            onClick = { rotation = deg },
                            label = { Text("$deg°") },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.det_display_invert), Modifier.weight(1f))
                    Switch(checked = inverted, onCheckedChange = { inverted = it })
                }
                Text(stringResource(R.string.det_display_timeout), style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    timeoutChoices.forEach { minutes ->
                        FilterChip(
                            selected = timeout == minutes,
                            onClick = { timeout = minutes },
                            label = { Text(displayTimeoutLabel(minutes)) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.det_display_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(DisplayControl(rotation, inverted, timeout)) }) {
                Text(stringResource(R.string.common_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
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
        title = { Text(stringResource(R.string.det_pool_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                current?.let {
                    Text(
                        stringResource(R.string.det_pool_current, it.first, it.second),
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                }
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.det_stratum_url)) }, singleLine = true)
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text(stringResource(R.string.det_port)) }, singleLine = true)
                OutlinedTextField(value = worker, onValueChange = { worker = it }, label = { Text(stringResource(R.string.det_worker)) }, singleLine = true)
                Text(
                    stringResource(R.string.det_pool_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.statusDegraded,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim(), portInt ?: 0, worker.trim()) },
                enabled = valid,
            ) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
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
        title = { Text(stringResource(R.string.det_fan_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.det_fan_auto), Modifier.padding(end = 12.dp))
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                if (!auto) {
                    Text(stringResource(R.string.det_fan_manual_speed, percent.roundToInt()))
                    Slider(value = percent, onValueChange = { percent = it }, valueRange = 20f..100f)
                    Text(
                        stringResource(R.string.det_fan_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.statusDegraded,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (auto) onAuto() else onManual(percent.roundToInt()) }) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Suppress("LongMethod") // declarative dialog layout; stringResource extraction added lines, not logic
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
            title = { Text(stringResource(R.string.det_tune_unavailable_title)) },
            text = {
                Text(
                    stringResource(R.string.det_tune_unavailable_body),
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
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
        title = { Text(stringResource(R.string.det_tune_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.det_tune_current,
                        "${currentFrequencyMhz ?: "?"}", "${currentVoltageMv ?: "?"}",
                    ) +
                        (options.defaultFrequencyMhz?.let {
                            stringResource(R.string.det_tune_stock, "$it", "${options.defaultVoltageMv}")
                        } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
                Text(stringResource(R.string.det_frequency_mhz), style = MaterialTheme.typography.labelMedium)
                OptionChips(options.frequencyOptionsMhz, freq, options.defaultFrequencyMhz) { freq = it }
                Text(stringResource(R.string.det_core_voltage_mv), style = MaterialTheme.typography.labelMedium)
                OptionChips(options.voltageOptionsMv, volt, options.defaultVoltageMv) { volt = it }
                Text(
                    stringResource(R.string.det_tune_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.statusDegraded,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(freq, volt) }) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = {
            Row {
                if (hasTuneToRollback) {
                    TextButton(onClick = onRollback) { Text(stringResource(R.string.det_roll_back)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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
