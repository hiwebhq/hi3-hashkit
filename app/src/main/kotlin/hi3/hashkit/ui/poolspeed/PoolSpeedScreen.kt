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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.R
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
)

@HiltViewModel
class PoolSpeedViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repository: MinerRepository,
    private val savedPoolDao: SavedPoolDao,
    private val tester: PoolSpeedTester,
) : ViewModel() {

    private val _state = MutableStateFlow(PoolSpeedState())
    val state: StateFlow<PoolSpeedState> = _state

    init {
        viewModelScope.launch { _state.value = _state.value.copy(candidates = collect().size) }
    }

    /**
     * Pools to test: each miner's actual reported pool (so a PRIVATE/LAN stratum is tested), plus
     * the address-book pools you've enabled for testing (the public pools now live there too).
     */
    private suspend fun collect(): List<Candidate> {
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
        return (fromMiners + fromSaved).distinctBy { "${it.host}:${it.port}" }
    }

    fun runTest() {
        if (_state.value.running) return
        viewModelScope.launch {
            val candidates = collect()
            if (candidates.isEmpty()) {
                _state.value = _state.value.copy(message = appContext.getString(R.string.vm_ps_no_pools))
                return@launch
            }
            _state.value = _state.value.copy(candidates = candidates.size, running = true, results = emptyList(), message = null)
            val results = mutableListOf<PoolSpeedResult>()
            for (c in candidates) {
                val r = tester.test(c.label, c.host, c.port)
                results += r
                _state.value = _state.value.copy(results = rank(results))
            }
            _state.value = _state.value.copy(running = false, message = appContext.getString(R.string.vm_ps_tested, candidates.size))
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

@Suppress("LongMethod") // declarative screen layout; stringResource extraction added lines, not logic
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
                title = { Text(stringResource(R.string.ps_title), fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.ps_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = viewModel::runTest, enabled = !state.running) {
                        Text(stringResource(if (state.running) R.string.ps_testing else R.string.ps_run))
                    }
                    if (state.running) CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                    Text(
                        stringResource(R.string.ps_pool_count, state.candidates),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }
            if (state.results.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.ps_ranked),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
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
                    if (!r.reachable) stringResource(R.string.ps_unreachable)
                    else (r.subscribeMs ?: r.connectMinMs)?.let { "$it ms" } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (!r.reachable) HiBrand.statusOffline else HiBrand.textPrimary,
                )
            }
            Text("${r.host}:${r.port}", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            if (r.reachable) {
                val connectPart = stringResource(R.string.ps_connect, "${r.connectMinMs ?: "—"}")
                val jitterPart = r.jitterMs?.let { stringResource(R.string.ps_jitter, it) }
                val stratumPart = stringResource(
                    R.string.ps_stratum,
                    r.subscribeMs?.let { "$it ms" } ?: stringResource(R.string.ps_no_response),
                )
                Text(
                    buildString {
                        append(connectPart)
                        jitterPart?.let { append(" · $it") }
                        append("  ·  ")
                        append(stratumPart)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (r.speaksStratum) HiBrand.textSecondary else HiBrand.statusDegraded,
                )
            }
        }
    }
}
