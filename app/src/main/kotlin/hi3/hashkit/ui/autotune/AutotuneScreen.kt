package hi3.hashkit.ui.autotune

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.R
import hi3.hashkit.core.Units
import hi3.hashkit.ui.theme.HiBrand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutotuneScreen(
    onBack: () -> Unit,
    viewModel: AutotuneViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val optimizer by viewModel.optimizer.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    var settle by remember { mutableStateOf(60) }
    var maxTemp by remember { mutableStateOf(70) }
    var optimizeHashrate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tune_title), fontWeight = FontWeight.Bold) },
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
        containerColor = HiBrand.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.tune_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }
            if (!state.supported) {
                item {
                    Text(
                        state.message ?: stringResource(R.string.tune_not_supported),
                        color = HiBrand.statusDegraded,
                    )
                }
                return@LazyColumn
            }
            insights?.let { ins -> item { TuneInsightsCard(ins) } }
            item {
                IntChipRow(stringResource(R.string.tune_settle_per_step), listOf(60, 90, 120), settle, !state.running, "s") {
                    settle = it
                }
            }
            item {
                IntChipRow(stringResource(R.string.tune_temp_ceiling), listOf(65, 70, 75), maxTemp, !state.running, "°C") {
                    maxTemp = it
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tune_optimize_for), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = !optimizeHashrate,
                        onClick = { optimizeHashrate = false },
                        enabled = !state.running,
                        label = { Text(stringResource(R.string.tune_efficiency)) },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    FilterChip(
                        selected = optimizeHashrate,
                        onClick = { optimizeHashrate = true },
                        enabled = !state.running,
                        label = { Text(stringResource(R.string.tune_hashrate)) },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            item {
                if (state.running) {
                    val frac = if (state.stepTotal > 0) state.stepIndex.toFloat() / state.stepTotal else 0f
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.tune_step_progress, state.stepIndex, state.stepTotal, state.currentLabel),
                        style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.cancel() }) { Text(stringResource(R.string.common_cancel)) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { viewModel.start(settle, maxTemp, optimizeHashrate) }) {
                            Text(stringResource(R.string.tune_start_sweep))
                        }
                        val best = state.bestFrequencyMhz
                        if (best != null) {
                            Button(onClick = { viewModel.applyBest() }) {
                                Text(stringResource(R.string.tune_apply_best, best))
                            }
                        }
                    }
                }
            }
            state.message?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary) }
            }
            if (state.results.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.tune_results_header), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                }
                items(state.results, key = { it.frequencyMhz }) { r ->
                    ResultRow(r, isBest = r.frequencyMhz == state.bestFrequencyMhz)
                }
            }

            // Persistent tuning optimizer: recommendation built from every past sweep.
            if (optimizer.sampleCount > 0) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.tune_optimizer_header, optimizer.sampleCount),
                            style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { viewModel.clearHistory() }, enabled = !state.running) {
                            Text(stringResource(R.string.tune_clear))
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            optimizer.bestEfficiency?.let {
                                Text(
                                    stringResource(
                                        R.string.tune_most_efficient,
                                        it.frequencyMhz, it.voltageMv, "%.1f".format(it.efficiencyJTh),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium, color = HiBrand.textPrimary,
                                )
                            }
                            optimizer.bestHashrate?.let {
                                Text(
                                    stringResource(
                                        R.string.tune_most_hashrate,
                                        it.frequencyMhz, it.voltageMv, Units.formatHashrate(it.hashrateGhs),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium, color = HiBrand.textPrimary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Text(
                                stringResource(R.string.tune_optimizer_hint),
                                style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                if (optimizer.efficiencyCurve.isNotEmpty()) {
                    item { Text(stringResource(R.string.tune_efficiency_curve), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
                    items(optimizer.efficiencyCurve, key = { "curve-${it.frequencyMhz}" }) { p ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${p.frequencyMhz} MHz", style = MaterialTheme.typography.bodyMedium, color = HiBrand.textPrimary)
                            Text(
                                "%.1f J/TH · %s".format(p.efficiencyJTh, Units.formatHashrate(p.hashrateGhs)),
                                style = MaterialTheme.typography.bodyMedium, color = HiBrand.textSecondary,
                            )
                        }
                    }
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
                Text("${r.frequencyMhz} MHz @ ${r.voltageMv} mV" + if (r.overTemp) stringResource(R.string.tune_over_ceiling) else "",
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

/** Approximate seconds represented by one telemetry sample (default poll cadence). */
private const val SECONDS_PER_SAMPLE = 15.0
private const val SECONDS_PER_HOUR = 3600.0
private const val GHS_PER_THS = 1000.0
private const val PEER_LAG_WARN_PCT = -10.0

@Composable
private fun IntChipRow(
    label: String,
    options: List<Int>,
    selected: Int,
    enabled: Boolean,
    suffix: String,
    onSelect: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        options.forEach { v ->
            FilterChip(
                selected = selected == v,
                onClick = { onSelect(v) },
                enabled = enabled,
                label = { Text("$v$suffix") },
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun TuneInsightsCard(ins: AutotuneViewModel.TuneInsightsUi) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.tune_observed_points),
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
            ins.periods.forEach { p ->
                val marks = buildString {
                    if (p === ins.bestEfficiency) append(stringResource(R.string.tune_mark_best_efficiency))
                    if (p === ins.bestHashrate) append(stringResource(R.string.tune_mark_best_rate))
                }
                Text(
                    stringResource(
                        R.string.tune_observed_point,
                        p.frequencyMhz, p.coreVoltageMv,
                        (p.avgHashrateGhs ?: 0.0) / GHS_PER_THS,
                        p.avgEfficiencyJTh?.let { "%.1f".format(it) } ?: "—",
                        p.maxChipTempC?.let { "%.0f°C".format(it) } ?: "—",
                        p.samples * SECONDS_PER_SAMPLE / SECONDS_PER_HOUR,
                        marks,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (marks.isNotEmpty()) HiBrand.accent else HiBrand.textPrimary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            ins.peerGapPercent?.let { gap ->
                Text(
                    stringResource(
                        R.string.tune_peer_gap,
                        ins.peerCount,
                        (if (gap >= 0) "+" else "") + "%.1f%%".format(gap),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (gap < PEER_LAG_WARN_PCT) HiBrand.statusDegraded else HiBrand.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
