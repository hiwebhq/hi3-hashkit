package hi3.hashkit.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.ui.dashboard.DashboardViewModel
import hi3.hashkit.ui.theme.HiBrand

private enum class RankBy(val label: String) { EFFICIENCY("Efficiency"), ATTAINMENT("Attainment"), HASHRATE("Hashrate") }

private fun attainment(m: Miner): Double? {
    val actual = m.lastTelemetry?.hashrateGhs?.value ?: return null
    val expected = m.lastTelemetry?.expectedHashrateGhs?.value ?: m.expectedHashrateGhs ?: return null
    if (expected <= 0) return null
    return actual / expected * 100.0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
    onMinerClick: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var rankBy by remember { mutableStateOf(RankBy.EFFICIENCY) }

    // Only live miners with the relevant metric are ranked; others are listed as "no data".
    val live = state.miners.filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
    val ranked = when (rankBy) {
        // Lower J/TH is better.
        RankBy.EFFICIENCY -> live.filter { it.lastTelemetry?.efficiencyJTh?.value != null }
            .sortedBy { it.lastTelemetry!!.efficiencyJTh.value }
        RankBy.ATTAINMENT -> live.filter { attainment(it) != null }.sortedByDescending { attainment(it) }
        RankBy.HASHRATE -> live.filter { it.lastTelemetry?.hashrateGhs?.value != null }
            .sortedByDescending { it.lastTelemetry!!.hashrateGhs.value }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leaderboard", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RankBy.entries.forEach { r ->
                        FilterChip(selected = rankBy == r, onClick = { rankBy = r }, label = { Text(r.label) })
                    }
                }
            }
            item {
                Text(
                    when (rankBy) {
                        RankBy.EFFICIENCY -> "Ranked by energy efficiency (J/TH) — lower is better. Needs power data."
                        RankBy.ATTAINMENT -> "Ranked by actual vs expected hashrate. Needs an expected hashrate."
                        RankBy.HASHRATE -> "Ranked by current hashrate."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
            if (ranked.isEmpty()) {
                item {
                    Text(
                        "No live miners with this metric yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HiBrand.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            itemsIndexed(ranked, key = { _, m -> m.id }) { index, m ->
                LeaderRow(index + 1, m, rankBy, onClick = { onMinerClick(m.id) })
            }
        }
    }
}

@Composable
private fun LeaderRow(rank: Int, m: Miner, rankBy: RankBy, onClick: () -> Unit) {
    val medal = when (rank) { 1 -> HiBrand.accent; 2 -> HiBrand.accentAlt; 3 -> HiBrand.statusDegraded; else -> HiBrand.surface }
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).background(medal, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("$rank", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = HiBrand.background) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = HiBrand.textPrimary)
                Text(
                    m.identity.model ?: m.host,
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
            val primary = when (rankBy) {
                RankBy.EFFICIENCY -> m.lastTelemetry?.efficiencyJTh?.value?.let { "%.1f J/TH".format(it) } ?: "—"
                RankBy.ATTAINMENT -> attainment(m)?.let { "%.0f%%".format(it) } ?: "—"
                RankBy.HASHRATE -> Units.formatHashrate(m.lastTelemetry?.hashrateGhs?.value)
            }
            Text(primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = HiBrand.accent)
        }
    }
}
