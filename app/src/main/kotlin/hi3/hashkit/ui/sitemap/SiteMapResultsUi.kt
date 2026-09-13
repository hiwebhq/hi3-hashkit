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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
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
            "Results (${rows.size})", style = MaterialTheme.typography.titleSmall,
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
            OutlinedButton(
                onClick = {
                    vm.exportCsv()?.let { context.shareFile(it, "text/csv", "Site map CSV") }
                },
                modifier = Modifier.weight(1f), enabled = state.captured.isNotEmpty(),
            ) { Text("Export CSV") }
            TextButton(onClick = vm::newSession, modifier = Modifier.weight(1f)) { Text("New session") }
        }
        OutlinedButton(
            onClick = vm::enrich, modifier = Modifier.fillMaxWidth(),
            enabled = !state.enriching && state.captured.isNotEmpty(),
        ) {
            Text(
                if (state.enriching) "Scanning… ${state.enrichProgress}/${state.captured.size}"
                else "Scan miners (API): MAC · serial · pool · worker · hashrate",
            )
        }
        OutlinedTextField(
            value = farmName, onValueChange = { farmName = it },
            label = { Text("Farm name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = state.farms.takeIf { it.isNotEmpty() }?.let { farms ->
                { Text("Existing: " + farms.joinToString { it.name }) }
            },
        )
        Button(
            onClick = { vm.saveToFarm(farmName) }, modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving && state.captured.isNotEmpty() && farmName.isNotBlank(),
        ) { Text(if (state.saving) "Saving…" else "Save to farm") }
        state.saveResult?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.statusOnline)
        }
    }
}

@Composable
private fun ResultHeader() {
    val style = MaterialTheme.typography.labelSmall
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("Location", Modifier.weight(W_LOCATION), style = style, color = HiBrand.textSecondary)
        Text("IP", Modifier.weight(W_IP), style = style, color = HiBrand.textSecondary)
        Text("MAC", Modifier.weight(W_MAC), style = style, color = HiBrand.textSecondary)
        Text("Source", Modifier.weight(W_SOURCE), style = style, color = HiBrand.textSecondary)
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
            val source = if (row.manual) "manual" else "button"
            Text(source, Modifier.weight(W_SOURCE), style = style, color = HiBrand.textSecondary)
        }
        if (info != null) {
            val line = if (info.error != null) {
                "scan failed: ${info.error}"
            } else {
                listOfNotNull(
                    info.model, info.serial?.let { "SN $it" }, info.pool, info.worker,
                    info.hashrateGhs?.let { hi3.hashkit.core.Units.formatHashrate(it) },
                ).joinToString(" · ").ifBlank { "no details reported" }
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
        title = { Text("Manual fill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Type the IP of the miner at the current slot — for gear without an " +
                        "IP Report button (WhatsMiner, Canaan, Bitaxe).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it }, label = { Text("IPv4 address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onFill(ip) }, enabled = ip.isNotBlank()) { Text("Fill slot") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
