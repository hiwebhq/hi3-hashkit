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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hi3.hashkit.R
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
                title = { Text(stringResource(R.string.dash_bulk_pool_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.dash_field_stratum_url)) }, singleLine = true)
                        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text(stringResource(R.string.dash_field_port)) }, singleLine = true)
                        OutlinedTextField(value = worker, onValueChange = { worker = it }, label = { Text(stringResource(R.string.dash_field_worker)) }, singleLine = true)
                        Text(
                            stringResource(R.string.dash_bulk_pool_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = HiBrand.statusDegraded,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = url.isNotBlank() && worker.isNotBlank() && portInt != null && portInt in 1..65535,
                        onClick = { onPlan(BulkAction.SetPool(url.trim(), portInt ?: 0, worker.trim())) },
                    ) { Text(stringResource(R.string.dash_preview)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
            )
        }
        BulkActionKind.FAN -> {
            var auto by rememberSaveable { mutableStateOf(true) }
            var percent by rememberSaveable { mutableStateOf(70f) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.dash_bulk_fan_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.dash_fan_automatic), Modifier.padding(end = 12.dp))
                            Switch(checked = auto, onCheckedChange = { auto = it })
                        }
                        if (!auto) {
                            Text(stringResource(R.string.dash_fan_manual_speed, percent.roundToInt()))
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
                    }) { Text(stringResource(R.string.dash_preview)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
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
        title = { Text(stringResource(plan.action.labelRes, *plan.action.labelArgs.toTypedArray())) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.dash_plan_will_run, plan.supported.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = HiBrand.statusOnline,
                )
                plan.supported.forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodySmall) }
                if (plan.skipped.isNotEmpty()) {
                    Text(
                        stringResource(R.string.dash_plan_skipped, plan.skipped.size),
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
                        stringResource(R.string.dash_plan_reboot_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.statusDegraded,
                    )
                }
                if (running) {
                    Text(stringResource(R.string.dash_plan_executing), color = HiBrand.accentAlt)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExecute, enabled = !running && plan.supported.isNotEmpty()) {
                Text(stringResource(R.string.dash_plan_run_on, plan.supported.size), color = HiBrand.statusDegraded)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !running) { Text(stringResource(R.string.common_cancel)) }
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
                if (failed == 0) stringResource(R.string.dash_results_done, ok)
                else stringResource(R.string.dash_results_partial, ok, failed),
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
                        is ActionResult.Success -> stringResource(R.string.dash_result_ok) to HiBrand.statusOnline
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
                        stringResource(R.string.dash_results_skipped, skippedCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
