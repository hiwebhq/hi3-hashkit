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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                title = { Text("Farms", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add farm")
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
                    "Group miners into farms/sites, each with its own scan subnet. The " +
                        "default farm opens on launch; the active farm is what the dashboard " +
                        "shows. Deleting a farm keeps its miners (they become unassigned).",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }
            if (rows.isEmpty()) {
                item {
                    Text(
                        "No farms yet. Add one to organize miners by site — handy for ASIC " +
                            "discovery and per-site log audits later.",
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
            title = { Text("Scan the new farm?") },
            text = { Text("Scan $subnet now for miners and add them to this farm?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.scanFarm(id)
                    rescanPrompt = null
                }) { Text("Scan now") }
            },
            dismissButton = {
                TextButton(onClick = { rescanPrompt = null }) { Text("Later") }
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
                    AssistChip(onClick = {}, enabled = false, label = { Text("Default") },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) })
                }
            }
            Text(
                (row.farm.subnetsCsv.ifBlank { "no subnet" }) + " · ${row.minerCount} miner(s)",
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isActive) {
                    AssistChip(onClick = {}, enabled = false, label = { Text("Viewing") },
                        leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) })
                } else {
                    TextButton(onClick = onSetActive) { Text("View") }
                }
                if (!row.farm.isDefault) {
                    TextButton(onClick = onSetDefault) { Text("Set default") }
                }
                TextButton(onClick = onScan) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.height(16.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Scan")
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = HiBrand.statusOffline,
                        modifier = Modifier.height(16.dp))
                    Text("Delete", color = HiBrand.statusOffline)
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
        title = { Text("Delete ${row.farm.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (alsoMiners) {
                        "Its ${row.minerCount} miner(s) and their history are deleted with it."
                    } else {
                        "Its ${row.minerCount} miner(s) stay tracked but become unassigned. History is kept."
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = alsoMiners,
                        onCheckedChange = { alsoMiners = it },
                    )
                    Text("Also delete its ${row.minerCount} miner(s)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoMiners) }) {
                Text("Delete", color = HiBrand.statusOffline)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
        title = { Text("Add farm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Home lab") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = subnet,
                    onValueChange = { subnet = it },
                    label = { Text("Scan subnet (CIDR)") },
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
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
