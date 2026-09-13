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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hi3.hashkit.R
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
    onBlink: (Boolean) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            stringResource(R.string.table_selected_count, count),
            style = MaterialTheme.typography.labelSmall,
            color = HiBrand.accent,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAssignFarm) { Text(stringResource(R.string.table_assign_to_farm)) }
            // Locate lights (miners whose firmware has no locate control are skipped).
            OutlinedButton(onClick = { onBlink(true) }) { Text(stringResource(R.string.table_blink_led)) }
            OutlinedButton(onClick = { onBlink(false) }) { Text(stringResource(R.string.table_stop_blink)) }
            OutlinedButton(onClick = onDelete) {
                Text(stringResource(R.string.common_delete), color = HiBrand.statusOffline)
            }
            if (count < filteredCount) {
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.table_select_all, filteredCount))
                }
            }
            TextButton(onClick = onClear) { Text(stringResource(R.string.table_clear)) }
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
        title = { Text(stringResource(R.string.table_assign_title, count)) },
        text = {
            Column {
                if (farms.isEmpty()) {
                    Text(
                        stringResource(R.string.table_no_farms),
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                }
                farms.forEach { farm ->
                    TextButton(onClick = { onPick(farm.id) }) { Text(farm.name) }
                }
                TextButton(onClick = { onPick(null) }) {
                    Text(stringResource(R.string.table_no_farm_unassign), color = HiBrand.textSecondary)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
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
        title = { Text(stringResource(R.string.table_delete_title, count)) },
        text = {
            Text(stringResource(R.string.table_delete_body))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_delete), color = HiBrand.statusOffline)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
