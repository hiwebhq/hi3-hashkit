package hi3.hashkit.ui.poolspeed

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.integrations.poolspeed.PoolSpeedResult
import hi3.hashkit.integrations.poolspeed.PoolSpeedTester
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private data class Candidate(val label: String, val host: String, val port: Int)

data class PoolSpeedState(
    val candidates: Int = 0,
    val running: Boolean = false,
    val results: List<PoolSpeedResult> = emptyList(),
    val message: String? = null,
    /** Also test the well-known public pools (off by default; your own pools are always tested). */
    val includePublic: Boolean = false,
)

@HiltViewModel
class PoolSpeedViewModel @Inject constructor(
    private val repository: MinerRepository,
    private val savedPoolDao: SavedPoolDao,
    private val tester: PoolSpeedTester,
) : ViewModel() {

    private val _state = MutableStateFlow(PoolSpeedState())
    val state: StateFlow<PoolSpeedState> = _state

    init {
        viewModelScope.launch { _state.value = _state.value.copy(candidates = collect(false).size) }
    }

    fun setIncludePublic(v: Boolean) {
        _state.value = _state.value.copy(includePublic = v)
        viewModelScope.launch { _state.value = _state.value.copy(candidates = collect(v).size) }
    }

    /**
     * Pools to test. Your fleet's actual pools (from each miner's reported config — this is
     * how a PRIVATE/LAN stratum gets tested) and saved address-book pools are always included.
     * The well-known PUBLIC pools are added only when [includePublic] is on.
     */
    private suspend fun collect(includePublic: Boolean): List<Candidate> {
        val fromMiners = repository.observeMinerEntities().first()
            .filter { !it.isDemo }
            .map { repository.toDomain(it, Instant.now()) }
            .mapNotNull { m ->
                val url = m.lastTelemetry?.poolUrl ?: return@mapNotNull null
                val port = m.lastTelemetry?.poolPort ?: 3333
                PoolSpeedTester.parseStratum(url, port)?.let { (h, p) -> Candidate(h, h, p) }
            }
        val fromSaved = savedPoolDao.observeAll().first()
            .filter { it.includeInTest }
            .mapNotNull { sp ->
                PoolSpeedTester.parseStratum(sp.url, sp.port)?.let { (h, p) -> Candidate(sp.label, h, p) }
            }
        val fromPublic = if (!includePublic) emptyList() else
            hi3.hashkit.integrations.hi3.PoolType.entries
                .filter { !it.comingSoon && it.stratumHost != null }
                .map { Candidate("${it.displayName} (public)", it.stratumHost!!, it.stratumPort) }
        return (fromMiners + fromSaved + fromPublic).distinctBy { "${it.host}:${it.port}" }
    }

    fun runTest() {
        if (_state.value.running) return
        viewModelScope.launch {
            val candidates = collect(_state.value.includePublic)
            if (candidates.isEmpty()) {
                _state.value = _state.value.copy(message = "No pools found. Configure a pool on a miner or in the address book.")
                return@launch
            }
            _state.value = _state.value.copy(candidates = candidates.size, running = true, results = emptyList(), message = null)
            val results = mutableListOf<PoolSpeedResult>()
            for (c in candidates) {
                val r = tester.test(c.label, c.host, c.port)
                results += r
                _state.value = _state.value.copy(results = rank(results))
            }
            _state.value = _state.value.copy(running = false, message = "Tested ${candidates.size} pool(s).")
        }
    }

    private fun rank(rs: List<PoolSpeedResult>): List<PoolSpeedResult> =
        rs.sortedWith(
            compareBy(
                { !it.reachable },
                { it.subscribeMs ?: Long.MAX_VALUE },
                { it.connectMinMs ?: Long.MAX_VALUE },
            )
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolSpeedScreen(
    onBack: () -> Unit,
    viewModel: PoolSpeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pool speed test", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Measures stratum latency — the TCP handshake and a real mining.subscribe " +
                        "round-trip, no packet sniffing. It tests the pools your fleet actually " +
                        "uses (read from each miner's own pool config, so PRIVATE/LAN stratum " +
                        "works because the phone shares the fleet network) plus any saved pools. " +
                        "Measured from THIS phone's network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Also compare public pools", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "CKPool, OCEAN, F2Pool, Braiins, Public Pool — their documented public " +
                                "stratum endpoints (a regional server may be faster).",
                            style = MaterialTheme.typography.labelSmall,
                            color = HiBrand.textSecondary,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = state.includePublic,
                        onCheckedChange = viewModel::setIncludePublic,
                        enabled = !state.running,
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = viewModel::runTest, enabled = !state.running) {
                        Text(if (state.running) "Testing…" else "Run speed test")
                    }
                    if (state.running) CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                    Text("${state.candidates} pool(s)", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                }
            }
            if (state.results.isNotEmpty()) {
                item { Text("RANKED (fastest first)", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
                items(state.results, key = { "${it.host}:${it.port}" }) { r ->
                    ResultCard(r, isBest = r == state.results.firstOrNull { it.reachable })
                }
            }
            state.message?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
            }
        }
    }
}

@Composable
private fun ResultCard(r: PoolSpeedResult, isBest: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isBest) HiBrand.accent.copy(alpha = 0.15f) else HiBrand.surface,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    (if (isBest) "★ " else "") + r.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isBest) HiBrand.accent else HiBrand.textPrimary,
                )
                Text(
                    if (!r.reachable) "unreachable"
                    else (r.subscribeMs ?: r.connectMinMs)?.let { "$it ms" } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (!r.reachable) HiBrand.statusOffline else HiBrand.textPrimary,
                )
            }
            Text("${r.host}:${r.port}", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            if (r.reachable) {
                Text(
                    buildString {
                        append("connect ${r.connectMinMs ?: "—"} ms")
                        r.jitterMs?.let { append(" · jitter ±$it ms") }
                        append("  ·  stratum ")
                        append(r.subscribeMs?.let { "$it ms" } ?: "no response")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (r.speaksStratum) HiBrand.textSecondary else HiBrand.statusDegraded,
                )
            }
        }
    }
}
