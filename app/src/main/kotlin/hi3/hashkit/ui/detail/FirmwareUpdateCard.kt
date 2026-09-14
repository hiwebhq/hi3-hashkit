package hi3.hashkit.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hi3.hashkit.R
import hi3.hashkit.integrations.update.FirmwareUpdateChecker
import hi3.hashkit.integrations.update.FirmwareUpdater.Step
import hi3.hashkit.ui.theme.HiBrand

private const val PERCENT = 100

/**
 * One-tap AxeOS update for this miner: the available release, a confirm dialog that
 * spells out what happens, then live progress through download → upload → reboot →
 * verify, ending in a clear success or failure.
 */
@Suppress("LongMethod") // declarative: one branch per update state plus the confirm dialog
@Composable
fun FirmwareUpdateCard(
    release: FirmwareUpdateChecker.Release?,
    runningVersion: String?,
    step: Step?,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onReleaseNotes: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (step) {
            null -> {
                release?.let {
                    Text(
                        stringResource(R.string.det_fw_available, it.tag, runningVersion ?: "?"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { confirm = true }) { Text(stringResource(R.string.det_fw_update_now)) }
                        TextButton(onClick = onReleaseNotes) { Text(stringResource(R.string.det_fw_release_notes)) }
                    }
                }
            }
            is Step.Done -> {
                Text(
                    stringResource(R.string.det_fw_done, step.version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HiBrand.statusOnline,
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
            }
            is Step.Failed -> {
                Text(
                    stringResource(R.string.det_fw_failed, step.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HiBrand.statusDegraded,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
                    if (release != null) {
                        TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.det_fw_retry)) }
                    }
                }
            }
            else -> ProgressLine(step)
        }
    }

    if (confirm && release != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.det_fw_confirm_title, release.tag)) },
            text = {
                Text(
                    stringResource(
                        R.string.det_fw_confirm_body,
                        String.format(java.util.Locale.US, "%.1f", (release.firmware?.sizeBytes ?: 0L) / BYTES_PER_MB),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = false; onUpdate() }) {
                    Text(stringResource(R.string.det_fw_update_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun ProgressLine(step: Step) {
    fun pct(p: Float) = (p * PERCENT).toInt()
    val (label, progress) = when (step) {
        is Step.Downloading -> stringResource(R.string.det_fw_step_download, pct(step.progress)) to step.progress
        is Step.UploadingWebUi -> stringResource(R.string.det_fw_step_www, pct(step.progress)) to step.progress
        is Step.UploadingFirmware -> stringResource(R.string.det_fw_step_firmware, pct(step.progress)) to step.progress
        Step.Rebooting -> stringResource(R.string.det_fw_step_reboot) to null
        Step.Verifying -> stringResource(R.string.det_fw_step_verify) to null
        is Step.Done, is Step.Failed -> "" to null
    }
    Text(label, style = MaterialTheme.typography.bodyMedium)
    if (progress != null) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    Text(
        stringResource(R.string.det_fw_keep_powered),
        style = MaterialTheme.typography.labelSmall,
        color = HiBrand.statusDegraded,
    )
}

private const val BYTES_PER_MB = 1_048_576.0
