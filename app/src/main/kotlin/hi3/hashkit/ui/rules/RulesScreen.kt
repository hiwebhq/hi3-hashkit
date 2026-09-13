package hi3.hashkit.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.R
import hi3.hashkit.data.db.RuleDao
import hi3.hashkit.data.db.RuleEntity
import hi3.hashkit.domain.rules.RuleEngine
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(private val dao: RuleDao) : ViewModel() {
    val rules = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(rule: RuleEntity, enabled: Boolean) {
        viewModelScope.launch { dao.update(rule.copy(enabled = enabled)) }
    }
    fun delete(id: Long) { viewModelScope.launch { dao.delete(id) } }
    fun save(rule: RuleEntity) { viewModelScope.launch { dao.upsert(rule) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onBack: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules_title), fontWeight = FontWeight.Bold) },
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
            FloatingActionButton(onClick = { editing = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.rules_add_rule))
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
                    stringResource(R.string.rules_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }
            items(rules, key = { it.id }) { rule ->
                RuleRow(
                    rule = rule,
                    onToggle = { viewModel.setEnabled(rule, it) },
                    onDelete = { viewModel.delete(rule.id) },
                )
            }
        }
    }

    if (editing) {
        RuleEditorDialog(
            onSave = { viewModel.save(it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

@Suppress("LongMethod") // declarative row layout; stringResource extraction added lines, not logic
@Composable
private fun RuleRow(rule: RuleEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val condition = RuleEngine.ConditionType.fromName(rule.conditionType)
    val action = RuleEngine.ActionType.fromName(rule.actionType)
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
                Text(rule.label, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = rule.enabled, onCheckedChange = onToggle)
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = HiBrand.statusOffline,
                        )
                    }
                }
            }
            val thr = rule.threshold?.let { " ${it.toInt()}${condition?.unit ?: ""}" } ?: ""
            val sustained = if (rule.sustainedForMinutes > 0) {
                stringResource(R.string.rules_row_sustained, rule.sustainedForMinutes)
            } else {
                ""
            }
            Text(
                stringResource(
                    R.string.rules_row_if,
                    condition?.let { stringResource(it.labelRes) } ?: rule.conditionType,
                    thr,
                    sustained,
                    action?.let { stringResource(it.labelRes) } ?: rule.actionType,
                ) +
                    (
                        rule.targetGroup?.let { stringResource(R.string.rules_row_group, it) }
                            ?: stringResource(R.string.rules_row_all_miners)
                        ) +
                    stringResource(R.string.rules_row_interval, rule.minIntervalMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.textSecondary,
            )
            rule.lastFiredAtEpochMs?.let {
                Text(
                    stringResource(
                        R.string.rules_last_fired,
                        java.text.DateFormat.getDateTimeInstance().format(Date(it)),
                        rule.lastResult.toString(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RuleEditorDialog(onSave: (RuleEntity) -> Unit, onDismiss: () -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var condition by rememberSaveable { mutableStateOf(RuleEngine.ConditionType.CHIP_TEMP_ABOVE) }
    var action by rememberSaveable { mutableStateOf(RuleEngine.ActionType.PAUSE) }
    var threshold by rememberSaveable { mutableStateOf("75") }
    var group by rememberSaveable { mutableStateOf("") }
    var interval by rememberSaveable { mutableStateOf("30") }
    var sustained by rememberSaveable { mutableStateOf("0") }

    val thresholdValid = !condition.needsThreshold || threshold.toDoubleOrNull() != null
    val valid = label.isNotBlank() && thresholdValid && (interval.toIntOrNull() ?: 0) >= 1 &&
        (sustained.toIntOrNull() ?: -1) >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_new_rule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.rules_name)) }, singleLine = true)
                Text(stringResource(R.string.rules_when), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleEngine.ConditionType.entries.forEach { c ->
                        FilterChip(selected = condition == c, onClick = { condition = c }, label = { Text(stringResource(c.labelRes)) })
                    }
                }
                if (condition.needsThreshold) {
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = { threshold = it },
                        label = { Text(stringResource(R.string.rules_threshold, condition.unit)) },
                        singleLine = true,
                    )
                }
                Text(stringResource(R.string.rules_then), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleEngine.ActionType.entries.forEach { a ->
                        FilterChip(selected = action == a, onClick = { action = a }, label = { Text(stringResource(a.labelRes)) })
                    }
                }
                OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text(stringResource(R.string.rules_target_group)) }, singleLine = true)
                OutlinedTextField(
                    value = sustained,
                    onValueChange = { sustained = it },
                    label = { Text(stringResource(R.string.rules_sustained_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text(stringResource(R.string.rules_min_interval_label)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.rules_editor_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        RuleEntity(
                            enabled = true,
                            label = label.trim(),
                            conditionType = condition.name,
                            threshold = if (condition.needsThreshold) threshold.toDoubleOrNull() else null,
                            actionType = action.name,
                            targetGroup = group.trim().takeIf { it.isNotBlank() },
                            minIntervalMinutes = interval.toIntOrNull()?.coerceAtLeast(1) ?: 30,
                            lastFiredAtEpochMs = null,
                            lastResult = null,
                            sustainedForMinutes = sustained.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        )
                    )
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
