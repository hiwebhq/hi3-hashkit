package hi3.hashkit.ui.addressbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.data.db.SavedPoolDao
import hi3.hashkit.data.db.SavedPoolEntity
import hi3.hashkit.data.repo.ControlRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddressBookViewModel @Inject constructor(
    private val dao: SavedPoolDao,
    private val minerRepository: MinerRepository,
    private val controlRepository: ControlRepository,
) : ViewModel() {

    val pools = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** host:port -> number of miners currently mining to it (their active pool). */
    val activeCounts: StateFlow<Map<String, Int>> =
        minerRepository.observeMinerEntities().map { entities ->
            entities.filter { !it.isDemo }.mapNotNull { e ->
                val m = minerRepository.toDomain(e, java.time.Instant.now())
                val url = m.lastTelemetry?.poolUrl ?: return@mapNotNull null
                val port = m.lastTelemetry?.poolPort ?: 3333
                hi3.hashkit.integrations.poolspeed.PoolSpeedTester.parseStratum(url, port)?.let { (h, p) -> "$h:$p" }
            }.groupingBy { it }.eachCount()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Key a saved pool the same way, so it can be matched against [activeCounts]. */
    fun activeKey(pool: SavedPoolEntity): String? =
        hi3.hashkit.integrations.poolspeed.PoolSpeedTester.parseStratum(pool.url, pool.port)?.let { (h, p) -> "$h:$p" }

    /** Non-null while a confirm dialog is up for applying a saved pool. */
    val pendingApply = MutableStateFlow<SavedPoolEntity?>(null)
    val applyMessage = MutableStateFlow<String?>(null)

    fun save(pool: SavedPoolEntity) { viewModelScope.launch { dao.upsert(pool) } }
    fun delete(id: Long) { viewModelScope.launch { dao.delete(id) } }
    fun setTest(pool: SavedPoolEntity, include: Boolean) {
        viewModelScope.launch { dao.update(pool.copy(includeInTest = include)) }
    }

    fun requestApply(pool: SavedPoolEntity) { pendingApply.value = pool }
    fun cancelApply() { pendingApply.value = null }

    /** Apply the saved pool to every real miner that supports a pool change. */
    fun confirmApply(group: String?) {
        val pool = pendingApply.value ?: return
        pendingApply.value = null
        viewModelScope.launch {
            val targets = minerRepository.observeMinerEntities().first()
                .filter { !it.isDemo && (group.isNullOrBlank() || it.groupName == group) }
            var ok = 0; var failed = 0; var skipped = 0
            for (entity in targets) {
                when (controlRepository.setPrimaryPool(entity, pool.url, pool.port, pool.worker)) {
                    is ActionResult.Success -> ok++
                    is ActionResult.Unsupported -> skipped++
                    else -> failed++
                }
            }
            applyMessage.value = "Applied \"${pool.label}\": $ok ok, $failed failed, $skipped unsupported."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookScreen(
    onBack: () -> Unit,
    viewModel: AddressBookViewModel = hiltViewModel(),
) {
    val pools by viewModel.pools.collectAsStateWithLifecycle()
    val activeCounts by viewModel.activeCounts.collectAsStateWithLifecycle()
    val pendingApply by viewModel.pendingApply.collectAsStateWithLifecycle()
    val applyMessage by viewModel.applyMessage.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var editPool by remember { mutableStateOf<SavedPoolEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pool address book", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editPool = null; editing = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add pool")
            }
        },
        containerColor = HiBrand.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Save the pools/wallets you use, then apply one across the fleet in a tap. " +
                        "The worker is the payout address (optionally address.workername). No pool " +
                        "password is stored — the firmware keeps its own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }
            applyMessage?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.bodySmall, color = HiBrand.accent) }
            }
            items(pools, key = { it.id }) { pool ->
                PoolRow(
                    pool = pool,
                    activeCount = activeCounts[viewModel.activeKey(pool)] ?: 0,
                    onApply = { viewModel.requestApply(pool) },
                    onEdit = { editPool = pool; editing = true },
                    onDelete = { viewModel.delete(pool.id) },
                    onToggleTest = { viewModel.setTest(pool, it) },
                )
            }
        }
    }

    if (editing) {
        PoolEditorDialog(
            existing = editPool,
            onSave = { viewModel.save(it); editing = false },
            onDismiss = { editing = false },
        )
    }

    pendingApply?.let { pool ->
        var group by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.cancelApply() },
            title = { Text("Apply \"${pool.label}\"?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Sets the primary pool to ${pool.url}:${pool.port} (worker ${pool.worker}) " +
                            "on all miners that support a pool change. Unsupported miners are skipped.",
                    )
                    OutlinedTextField(
                        value = group,
                        onValueChange = { group = it },
                        label = { Text("Limit to group (blank = all miners)") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmApply(group.trim().ifBlank { null }) }) {
                    Text("Apply to fleet", color = HiBrand.statusDegraded)
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.cancelApply() }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PoolRow(
    pool: SavedPoolEntity,
    activeCount: Int,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleTest: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pool.label, style = MaterialTheme.typography.titleSmall)
                    if (activeCount > 0) {
                        Text(
                            "  ● ACTIVE ($activeCount)",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiBrand.statusOnline,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = HiBrand.textSecondary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = HiBrand.statusOffline)
                    }
                }
            }
            Text(
                "${pool.url}:${pool.port}",
                style = MaterialTheme.typography.bodySmall, color = HiBrand.textPrimary,
            )
            Text(
                pool.worker,
                style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary, maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                OutlinedButton(onClick = onApply) { Text("Apply to fleet") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Speed test", style = MaterialTheme.typography.labelMedium, color = HiBrand.textSecondary)
                    androidx.compose.material3.Switch(
                        checked = pool.includeInTest,
                        onCheckedChange = onToggleTest,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PoolEditorDialog(existing: SavedPoolEntity?, onSave: (SavedPoolEntity) -> Unit, onDismiss: () -> Unit) {
    var label by rememberSaveable { mutableStateOf(existing?.label ?: "") }
    var url by rememberSaveable { mutableStateOf(existing?.url ?: "") }
    var port by rememberSaveable { mutableStateOf(existing?.port?.toString() ?: "3333") }
    var worker by rememberSaveable { mutableStateOf(existing?.worker ?: "") }

    val valid = label.isNotBlank() && url.isNotBlank() && worker.isNotBlank() &&
        (port.toIntOrNull() ?: 0) in 1..65535

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Saved pool" else "Edit pool") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Stratum URL") }, singleLine = true)
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, singleLine = true)
                OutlinedTextField(value = worker, onValueChange = { worker = it }, label = { Text("Worker / payout address") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        SavedPoolEntity(
                            id = existing?.id ?: 0,
                            label = label.trim(),
                            url = url.trim(),
                            port = port.toInt(),
                            worker = worker.trim(),
                            includeInTest = existing?.includeInTest ?: true,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
