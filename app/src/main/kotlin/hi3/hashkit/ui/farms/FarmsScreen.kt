package hi3.hashkit.ui.farms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.R
import hi3.hashkit.ui.theme.HiBrand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmsScreen(
    onBack: () -> Unit,
    viewModel: FarmsViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val activeFarmId by viewModel.activeFarmId.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    // After creating a farm we offer an immediate scan of its subnet.
    var rescanPrompt by remember { mutableStateOf<Pair<Long, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.farms_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.farms_add_farm))
            }
        },
        containerColor = HiBrand.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.farms_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }
            if (rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.farms_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HiBrand.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(rows, key = { it.farm.id }) { row ->
                FarmCard(
                    row = row,
                    isActive = row.farm.id == activeFarmId,
                    onSetActive = { viewModel.setActive(row.farm.id) },
                    onSetDefault = { viewModel.setDefault(row.farm.id) },
                    onScan = { viewModel.scanFarm(row.farm.id) },
                    onDelete = { alsoMiners -> viewModel.delete(row.farm.id, alsoMiners) },
                )
            }
        }
    }

    if (showAdd) {
        AddFarmDialog(
            prefillSubnet = viewModel.detectedCidr(),
            onDismiss = { showAdd = false },
            onCreate = { name, subnet ->
                showAdd = false
                viewModel.createFarm(name, subnet) { id ->
                    if (subnet.isNotBlank()) rescanPrompt = id to subnet
                }
            },
        )
    }

    rescanPrompt?.let { (id, subnet) ->
        AlertDialog(
            onDismissRequest = { rescanPrompt = null },
            title = { Text(stringResource(R.string.farms_scan_new_title)) },
            text = { Text(stringResource(R.string.farms_scan_new_body, subnet)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.scanFarm(id)
                    rescanPrompt = null
                }) { Text(stringResource(R.string.farms_scan_now)) }
            },
            dismissButton = {
                TextButton(onClick = { rescanPrompt = null }) { Text(stringResource(R.string.farms_later)) }
            },
        )
    }
}

@Composable
private fun FarmCard(
    row: FarmRow,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onSetDefault: () -> Unit,
    onScan: () -> Unit,
    onDelete: (alsoMiners: Boolean) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.farm.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HiBrand.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (row.farm.isDefault) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.farms_default)) },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) })
                }
            }
            Text(
                (row.farm.subnetsCsv.ifBlank { stringResource(R.string.farms_no_subnet) }) +
                    stringResource(R.string.farms_miner_count, row.minerCount),
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isActive) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.farms_viewing)) },
                        leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) })
                } else {
                    TextButton(onClick = onSetActive) { Text(stringResource(R.string.farms_view)) }
                }
                if (!row.farm.isDefault) {
                    TextButton(onClick = onSetDefault) { Text(stringResource(R.string.farms_set_default)) }
                }
                TextButton(onClick = onScan) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.height(16.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.farms_scan))
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = HiBrand.statusOffline,
                        modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.common_delete), color = HiBrand.statusOffline)
                }
            }
        }
    }
    if (confirmDelete) {
        DeleteFarmDialog(
            row = row,
            onConfirm = { alsoMiners -> confirmDelete = false; onDelete(alsoMiners) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DeleteFarmDialog(
    row: FarmRow,
    onConfirm: (alsoMiners: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var alsoMiners by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.farms_delete_title, row.farm.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (alsoMiners) {
                        stringResource(R.string.farms_delete_body_also, row.minerCount)
                    } else {
                        stringResource(R.string.farms_delete_body_keep, row.minerCount)
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = alsoMiners,
                        onCheckedChange = { alsoMiners = it },
                    )
                    Text(stringResource(R.string.farms_delete_also_checkbox, row.minerCount))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoMiners) }) {
                Text(stringResource(R.string.common_delete), color = HiBrand.statusOffline)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun AddFarmDialog(
    prefillSubnet: String,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var subnet by remember { mutableStateOf(prefillSubnet) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.farms_add_farm)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.farms_name)) },
                    placeholder = { Text(stringResource(R.string.farms_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = subnet,
                    onValueChange = { subnet = it },
                    label = { Text(stringResource(R.string.farms_subnet_label)) },
                    placeholder = { Text("192.168.1.0/24") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, subnet) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.farms_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
