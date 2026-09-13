package hi3.hashkit.ui.sitemap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import hi3.hashkit.R
import hi3.hashkit.ui.theme.HiBrand
import hi3.hashkit.ui.util.shareFile

// Results-table column weights.
private const val W_LOCATION = 1.1f
private const val W_IP = 1.0f
private const val W_MAC = 1.4f
private const val W_SOURCE = 0.7f

/** DONE-phase list items: export/save/scan actions plus the results table. */
internal fun LazyListScope.doneItems(vm: SiteMapViewModel, state: SiteMapState) {
    val rows = state.captured.toSortedMap().entries.toList()
    item { DoneActions(vm, state) }
    item {
        Text(
            stringResource(R.string.site_results, rows.size), style = MaterialTheme.typography.titleSmall,
            color = HiBrand.textPrimary, fontWeight = FontWeight.Bold,
        )
    }
    item { ResultHeader() }
    items(rows, key = { it.value.slot.code }) { (index, row) ->
        ResultRow(row, state.enriched[index])
    }
}

@Composable
private fun DoneActions(vm: SiteMapViewModel, state: SiteMapState) {
    val context = LocalContext.current
    var farmName by rememberSaveable(state.siteName) { mutableStateOf(state.siteName) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val shareTitle = stringResource(R.string.site_share_csv_title)
            OutlinedButton(
                onClick = {
                    vm.exportCsv()?.let { context.shareFile(it, "text/csv", shareTitle) }
                },
                modifier = Modifier.weight(1f), enabled = state.captured.isNotEmpty(),
            ) { Text(stringResource(R.string.site_export_csv)) }
            TextButton(onClick = vm::newSession, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.site_new_session)) }
        }
        OutlinedButton(
            onClick = vm::enrich, modifier = Modifier.fillMaxWidth(),
            enabled = !state.enriching && state.captured.isNotEmpty(),
        ) {
            Text(
                if (state.enriching) {
                    stringResource(R.string.site_scanning_progress, state.enrichProgress, state.captured.size)
                } else {
                    stringResource(R.string.site_scan_miners)
                },
            )
        }
        OutlinedTextField(
            value = farmName, onValueChange = { farmName = it },
            label = { Text(stringResource(R.string.site_farm_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = state.farms.takeIf { it.isNotEmpty() }?.let { farms ->
                { Text(stringResource(R.string.site_existing_farms, farms.joinToString { it.name })) }
            },
        )
        Button(
            onClick = { vm.saveToFarm(farmName) }, modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving && state.captured.isNotEmpty() && farmName.isNotBlank(),
        ) {
            Text(
                if (state.saving) stringResource(R.string.site_saving)
                else stringResource(R.string.site_save_to_farm)
            )
        }
        state.saveResult?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.statusOnline)
        }
    }
}

@Composable
private fun ResultHeader() {
    val style = MaterialTheme.typography.labelSmall
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.site_col_location), Modifier.weight(W_LOCATION), style = style, color = HiBrand.textSecondary)
        Text(stringResource(R.string.site_col_ip), Modifier.weight(W_IP), style = style, color = HiBrand.textSecondary)
        Text(stringResource(R.string.site_col_mac), Modifier.weight(W_MAC), style = style, color = HiBrand.textSecondary)
        Text(stringResource(R.string.site_col_source), Modifier.weight(W_SOURCE), style = style, color = HiBrand.textSecondary)
    }
}

@Composable
private fun ResultRow(row: CapturedSlot, info: EnrichedMiner?) {
    val style = MaterialTheme.typography.bodySmall
    Column(Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(row.slot.code, Modifier.weight(W_LOCATION), style = style, color = HiBrand.textPrimary)
            Text(row.ip, Modifier.weight(W_IP), style = style, color = HiBrand.textPrimary)
            Text(row.mac ?: "—", Modifier.weight(W_MAC), style = style, color = HiBrand.textSecondary)
            val source = if (row.manual) {
                stringResource(R.string.site_source_manual)
            } else {
                stringResource(R.string.site_source_button)
            }
            Text(source, Modifier.weight(W_SOURCE), style = style, color = HiBrand.textSecondary)
        }
        if (info != null) {
            val snPrefix = info.serial?.let { stringResource(R.string.site_sn_prefix, it) }
            val line = if (info.error != null) {
                stringResource(R.string.site_scan_failed, info.error)
            } else {
                listOfNotNull(
                    info.model, snPrefix, info.pool, info.worker,
                    info.hashrateGhs?.let { hi3.hashkit.core.Units.formatHashrate(it) },
                ).joinToString(" · ").ifBlank { stringResource(R.string.site_no_details) }
            }
            Text(
                line, style = MaterialTheme.typography.labelSmall,
                color = if (info.error != null) HiBrand.statusOffline else HiBrand.statusOnline,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
        }
    }
}

/** Fill the cursor's slot by typing an IP — for gear without an IP Report button. */
@Composable
internal fun ManualFillDialog(onFill: (String) -> Unit, onDismiss: () -> Unit) {
    var ip by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.site_manual_fill_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.site_manual_fill_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it }, label = { Text(stringResource(R.string.site_ipv4_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onFill(ip) }, enabled = ip.isNotBlank()) { Text(stringResource(R.string.site_fill_slot)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
