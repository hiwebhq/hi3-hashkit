package hi3.hashkit.ui.autotune

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import hi3.hashkit.core.Units
import hi3.hashkit.ui.theme.HiBrand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutotuneScreen(
    onBack: () -> Unit,
    viewModel: AutotuneViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var settle by remember { mutableStateOf(60) }
    var maxTemp by remember { mutableStateOf(70) }
    var optimizeHashrate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-tuner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        containerColor = HiBrand.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Sweeps the firmware-approved frequencies at your current voltage, lets each " +
                        "settle, measures J/TH and chip temp, then restores your original setpoint " +
                        "and recommends the best point under your temperature ceiling. It stops " +
                        "climbing once the ceiling is hit. Applying is a separate tap — keep the app " +
                        "open during the sweep.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }
            if (!state.supported) {
                item { Text(state.message ?: "Not supported on this miner.", color = HiBrand.statusDegraded) }
                return@LazyColumn
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Settle per step", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    listOf(60, 90, 120).forEach { s ->
                        FilterChip(
                            selected = settle == s,
                            onClick = { settle = s },
                            enabled = !state.running,
                            label = { Text("${s}s") },
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Temp ceiling", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    listOf(65, 70, 75).forEach { c ->
                        FilterChip(
                            selected = maxTemp == c,
                            onClick = { maxTemp = c },
                            enabled = !state.running,
                            label = { Text("${c}°C") },
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Optimize for", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = !optimizeHashrate,
                        onClick = { optimizeHashrate = false },
                        enabled = !state.running,
                        label = { Text("Efficiency") },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    FilterChip(
                        selected = optimizeHashrate,
                        onClick = { optimizeHashrate = true },
                        enabled = !state.running,
                        label = { Text("Hashrate") },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            item {
                if (state.running) {
                    val frac = if (state.stepTotal > 0) state.stepIndex.toFloat() / state.stepTotal else 0f
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("Step ${state.stepIndex}/${state.stepTotal} — ${state.currentLabel}",
                        style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.cancel() }) { Text("Cancel") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { viewModel.start(settle, maxTemp, optimizeHashrate) }) { Text("Start sweep") }
                        if (state.bestFrequencyMhz != null) {
                            Button(onClick = { viewModel.applyBest() }) { Text("Apply best (${state.bestFrequencyMhz} MHz)") }
                        }
                    }
                }
            }
            state.message?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary) }
            }
            if (state.results.isNotEmpty()) {
                item {
                    Text("RESULTS (best first)", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                }
                items(state.results, key = { it.frequencyMhz }) { r ->
                    ResultRow(r, isBest = r.frequencyMhz == state.bestFrequencyMhz)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(r: TuneResult, isBest: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isBest) HiBrand.accent.copy(alpha = 0.18f) else HiBrand.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${r.frequencyMhz} MHz @ ${r.voltageMv} mV" + if (r.overTemp) "  ⚠ over ceiling" else "",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (r.overTemp) HiBrand.statusOffline else HiBrand.textPrimary)
                Text(
                    "${Units.formatHashrate(r.hashrateGhs)} · ${Units.formatPower(r.powerW)}" +
                        (r.chipTempC?.let { " · ${it.toInt()}°C" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary,
                )
            }
            Text(
                r.efficiencyJTh?.let { "%.1f J/TH".format(it) } ?: "—",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = if (isBest) HiBrand.accent else HiBrand.textPrimary,
            )
        }
    }
}
