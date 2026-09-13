package hi3.hashkit.ui.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hi3.hashkit.data.db.FarmEntity
import hi3.hashkit.ui.theme.HiBrand

/** Action bar shown while Fleet-table rows are selected (long-press starts a selection). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TableSelectionBar(
    count: Int,
    filteredCount: Int,
    onSelectAll: () -> Unit,
    onAssignFarm: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            "$count selected",
            style = MaterialTheme.typography.labelSmall,
            color = HiBrand.accent,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAssignFarm) { Text("Assign to farm") }
            OutlinedButton(onClick = onDelete) { Text("Delete", color = HiBrand.statusOffline) }
            if (count < filteredCount) {
                TextButton(onClick = onSelectAll) { Text("Select all $filteredCount") }
            }
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

/** Pick the farm for the selected miners; "No farm" unassigns them. */
@Composable
internal fun AssignFarmDialog(
    farms: List<FarmEntity>,
    count: Int,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign $count miner(s) to…") },
        text = {
            Column {
                if (farms.isEmpty()) {
                    Text(
                        "No farms yet — create one under Advanced → Farms first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                }
                farms.forEach { farm ->
                    TextButton(onClick = { onPick(farm.id) }) { Text(farm.name) }
                }
                TextButton(onClick = { onPick(null) }) {
                    Text("No farm (unassign)", color = HiBrand.textSecondary)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun ConfirmDeleteSelectedDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $count miner(s)?") },
        text = {
            Text(
                "Removes them and their telemetry history from the app. " +
                    "The physical machines are not touched.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = HiBrand.statusOffline) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
