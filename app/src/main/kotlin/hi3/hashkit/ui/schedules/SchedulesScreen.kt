package hi3.hashkit.ui.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import hi3.hashkit.data.db.ScheduleDao
import hi3.hashkit.data.db.ScheduleEntity
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.util.Date
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SchedulesViewModel @Inject constructor(
    private val dao: ScheduleDao,
) : ViewModel() {
    val schedules = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(schedule: ScheduleEntity, enabled: Boolean) {
        viewModelScope.launch { dao.update(schedule.copy(enabled = enabled)) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { dao.delete(id) }
    }

    fun save(schedule: ScheduleEntity) {
        viewModelScope.launch { dao.upsert(schedule) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    onBack: () -> Unit,
    viewModel: SchedulesViewModel = hiltViewModel(),
) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedules", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add schedule")
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
                    "Schedules run when the app polls (foreground) or during background " +
                        "monitoring cycles (~15 min granularity) — not at exact times. A " +
                        "missed day is skipped, never replayed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }
            items(schedules, key = { it.id }) { schedule ->
                ScheduleRow(
                    schedule = schedule,
                    onToggle = { viewModel.setEnabled(schedule, it) },
                    onDelete = { viewModel.delete(schedule.id) },
                )
            }
        }
    }

    if (editing) {
        ScheduleEditorDialog(
            onSave = { viewModel.save(it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun ScheduleRow(
    schedule: ScheduleEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
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
                Text(schedule.label, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = schedule.enabled, onCheckedChange = onToggle)
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = HiBrand.statusOffline)
                    }
                }
            }
            Text(
                "${schedule.actionType} at %02d:%02d on %s · targets: %s".format(
                    schedule.timeMinutesOfDay / 60,
                    schedule.timeMinutesOfDay % 60,
                    schedule.daysOfWeekCsv.split(",").joinToString(" ") { it.take(3) },
                    schedule.targetGroup?.let { "group \"$it\"" } ?: "all miners",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.textSecondary,
            )
            schedule.lastRunAtEpochMs?.let {
                Text(
                    "Last run ${java.text.DateFormat.getDateTimeInstance().format(Date(it))}: ${schedule.lastResult}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ScheduleEditorDialog(
    onSave: (ScheduleEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var action by rememberSaveable { mutableStateOf("reboot") }
    var time by rememberSaveable { mutableStateOf("03:00") }
    var group by rememberSaveable { mutableStateOf("") }
    var days by remember { mutableStateOf(DayOfWeek.entries.map { it.name }.toSet()) }
    // Pool params
    var poolUrl by rememberSaveable { mutableStateOf("") }
    var poolPort by rememberSaveable { mutableStateOf("3333") }
    var poolWorker by rememberSaveable { mutableStateOf("") }
    // Fan params
    var fanAuto by rememberSaveable { mutableStateOf(true) }
    var fanPercent by rememberSaveable { mutableStateOf(70f) }

    val timeMinutes = time.split(":").let { parts ->
        val h = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val m = parts.getOrNull(1)?.trim()?.toIntOrNull()
        if (h != null && m != null && h in 0..23 && m in 0..59) h * 60 + m else null
    }
    val paramsValid = when (action) {
        "set_pool" -> poolUrl.isNotBlank() && poolWorker.isNotBlank() && poolPort.toIntOrNull() in 1..65535
        else -> true
    }
    val valid = label.isNotBlank() && timeMinutes != null && days.isNotEmpty() && paramsValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Name") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "reboot" to "Restart", "set_pool" to "Pool", "set_fan" to "Fan",
                        "pause" to "Pause", "resume" to "Resume",
                        "plug_off" to "Plug off", "plug_on" to "Plug on",
                    ).forEach { (key, text) ->
                        FilterChip(selected = action == key, onClick = { action = key }, label = { Text(text) })
                    }
                }
                when (action) {
                    "set_pool" -> {
                        OutlinedTextField(value = poolUrl, onValueChange = { poolUrl = it }, label = { Text("Stratum URL") }, singleLine = true)
                        OutlinedTextField(value = poolPort, onValueChange = { poolPort = it }, label = { Text("Port") }, singleLine = true)
                        OutlinedTextField(value = poolWorker, onValueChange = { poolWorker = it }, label = { Text("Worker") }, singleLine = true)
                    }
                    "set_fan" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Automatic", Modifier.padding(end = 10.dp))
                            Switch(checked = fanAuto, onCheckedChange = { fanAuto = it })
                        }
                        if (!fanAuto) {
                            Text("Manual: ${fanPercent.roundToInt()}%")
                            Slider(value = fanPercent, onValueChange = { fanPercent = it }, valueRange = 20f..100f)
                        }
                    }
                }
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time (HH:MM, device timezone)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day.name in days,
                            onClick = {
                                days = if (day.name in days) days - day.name else days + day.name
                            },
                            label = { Text(day.name.take(2)) },
                        )
                    }
                }
                OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("Target group (blank = all miners)") }, singleLine = true)
                Spacer(Modifier.height(2.dp))
                val hint = if (action == "plug_on" || action == "plug_off") {
                    "Plug on/off switches each target miner's configured smart plug over the LAN. " +
                        "Miners without a plug set up are skipped. Pair a nightly Plug off with a " +
                        "morning Plug on for time-of-use power control; each run is recorded."
                } else {
                    "Time-of-use: schedule Pause at your peak-rate start and Resume at the end " +
                        "(pause needs a device that supports it). Unsupported devices are skipped " +
                        "with a reason; each run is recorded in the audit log."
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val params = when (action) {
                        "set_pool" -> buildJsonObject {
                            put("url", poolUrl.trim())
                            put("port", poolPort.toInt())
                            put("worker", poolWorker.trim())
                        }.toString()
                        "set_fan" -> buildJsonObject {
                            put("auto", fanAuto)
                            if (!fanAuto) put("percent", fanPercent.roundToInt())
                        }.toString()
                        else -> "{}"
                    }
                    onSave(
                        ScheduleEntity(
                            enabled = true,
                            label = label.trim(),
                            actionType = action,
                            paramsJson = params,
                            targetMinerIdsCsv = "",
                            targetGroup = group.trim().takeIf { it.isNotBlank() },
                            timeMinutesOfDay = timeMinutes ?: 0,
                            daysOfWeekCsv = days.joinToString(","),
                            minIntervalMinutes = 60,
                            lastRunAtEpochMs = null,
                            lastResult = null,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
