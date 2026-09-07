package hi3.hashkit.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hi3.hashkit.data.repo.BulkAction
import hi3.hashkit.data.repo.BulkOutcome
import hi3.hashkit.data.repo.BulkPlan
import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.domain.adapter.FanControl
import hi3.hashkit.ui.theme.HiBrand
import kotlin.math.roundToInt

/** Which bulk action the user is configuring (params step, before plan preview). */
enum class BulkActionKind { REBOOT, POOL, FAN, PAUSE, RESUME }

@Composable
fun BulkParamsDialog(
    kind: BulkActionKind,
    onPlan: (BulkAction) -> Unit,
    onDismiss: () -> Unit,
) {
    when (kind) {
        BulkActionKind.REBOOT ->
            androidx.compose.runtime.LaunchedEffect(Unit) { onPlan(BulkAction.Reboot) }
        BulkActionKind.PAUSE ->
            androidx.compose.runtime.LaunchedEffect(Unit) {
                onPlan(BulkAction.Power(hi3.hashkit.domain.adapter.PowerAction.PAUSE))
            }
        BulkActionKind.RESUME ->
            androidx.compose.runtime.LaunchedEffect(Unit) {
                onPlan(BulkAction.Power(hi3.hashkit.domain.adapter.PowerAction.RESUME))
            }
        BulkActionKind.POOL -> {
            var url by rememberSaveable { mutableStateOf("") }
            var port by rememberSaveable { mutableStateOf("3333") }
            var worker by rememberSaveable { mutableStateOf("") }
            val portInt = port.toIntOrNull()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Bulk pool change") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Stratum URL") }, singleLine = true)
                        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, singleLine = true)
                        OutlinedTextField(value = worker, onValueChange = { worker = it }, label = { Text("Worker / address") }, singleLine = true)
                        Text(
                            "Applied to each selected miner's PRIMARY pool. Stored pool " +
                                "passwords are preserved. You will see a per-device preview next.",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiBrand.statusDegraded,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = url.isNotBlank() && worker.isNotBlank() && portInt != null && portInt in 1..65535,
                        onClick = { onPlan(BulkAction.SetPool(url.trim(), portInt ?: 0, worker.trim())) },
                    ) { Text("Preview") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
        BulkActionKind.FAN -> {
            var auto by rememberSaveable { mutableStateOf(true) }
            var percent by rememberSaveable { mutableStateOf(70f) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Bulk fan change") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Automatic (firmware-managed)", Modifier.padding(end = 12.dp))
                            Switch(checked = auto, onCheckedChange = { auto = it })
                        }
                        if (!auto) {
                            Text("Manual speed: ${percent.roundToInt()}%")
                            Slider(value = percent, onValueChange = { percent = it }, valueRange = 20f..100f)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        onPlan(
                            BulkAction.SetFan(
                                if (auto) FanControl.Automatic() else FanControl.Manual(percent.roundToInt())
                            )
                        )
                    }) { Text("Preview") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
    }
}

@Composable
fun BulkPlanDialog(
    plan: BulkPlan,
    running: Boolean,
    onExecute: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(plan.action.label) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Will run on ${plan.supported.size} miner(s):",
                    style = MaterialTheme.typography.labelMedium,
                    color = HiBrand.statusOnline,
                )
                plan.supported.forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodySmall) }
                if (plan.skipped.isNotEmpty()) {
                    Text(
                        "Skipped ${plan.skipped.size} (unsupported):",
                        style = MaterialTheme.typography.labelMedium,
                        color = HiBrand.statusDegraded,
                    )
                    plan.skipped.forEach { (miner, reason) ->
                        Text(
                            "• ${miner.name} — $reason",
                            style = MaterialTheme.typography.bodySmall,
                            color = HiBrand.textSecondary,
                        )
                    }
                }
                if (plan.action is BulkAction.Reboot) {
                    Text(
                        "Each miner stops hashing for ~30–60 s while it restarts.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.statusDegraded,
                    )
                }
                if (running) {
                    Text("Executing sequentially…", color = HiBrand.accentAlt)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExecute, enabled = !running && plan.supported.isNotEmpty()) {
                Text("Run on ${plan.supported.size} miner(s)", color = HiBrand.statusDegraded)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !running) { Text("Cancel") }
        },
    )
}

@Composable
fun BulkResultsDialog(
    outcomes: List<BulkOutcome>,
    skippedCount: Int,
    onDismiss: () -> Unit,
) {
    val ok = outcomes.count { it.result is ActionResult.Success }
    val failed = outcomes.size - ok
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (failed == 0) "Done: $ok succeeded" else "Partial: $ok succeeded, $failed failed",
                color = if (failed == 0) HiBrand.statusOnline else HiBrand.statusDegraded,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            ) {
                outcomes.forEach { outcome ->
                    val (label, color) = when (val r = outcome.result) {
                        is ActionResult.Success -> "ok" to HiBrand.statusOnline
                        is ActionResult.Failure -> r.message to HiBrand.statusOffline
                        is ActionResult.Unsupported -> r.reason to HiBrand.statusDegraded
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(outcome.minerName, style = MaterialTheme.typography.bodySmall)
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = color,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                if (skippedCount > 0) {
                    Text(
                        "$skippedCount unsupported miner(s) were skipped.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
