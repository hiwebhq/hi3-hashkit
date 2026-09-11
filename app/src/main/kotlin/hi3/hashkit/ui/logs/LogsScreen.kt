package hi3.hashkit.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.adapters.espminer.EspMinerLogStream
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogsUiState(
    val minerName: String = "",
    val lines: List<String> = emptyList(),
    val paused: Boolean = false,
    val status: String = "Connecting…",
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repository: MinerRepository,
    private val logStream: EspMinerLogStream,
    private val logRepository: hi3.hashkit.data.repo.LogRepository,
    settingsRepository: hi3.hashkit.data.prefs.SettingsRepository,
) : ViewModel() {

    private val minerId: Long = checkNotNull(savedStateHandle["minerId"])
    private val _state = MutableStateFlow(LogsUiState())
    val state = _state

    /** The log analyzer is an advanced feature; the toggle only shows when unlocked. */
    val advancedUnlocked = settingsRepository.settings
        .map { it.advancedUnlocked }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Analysis window in ms; 0 = the live in-memory buffer, else stored history. */
    val windowMs = MutableStateFlow(0L)

    /** Analysis for the Analyze panel — live buffer, or stored history over [windowMs]. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val analysis: StateFlow<hi3.hashkit.domain.logs.LogAnalyzer.Analysis> =
        windowMs.flatMapLatest { w ->
            if (w == 0L) _state.map { hi3.hashkit.domain.logs.LogAnalyzer.analyze(it.lines) }
            else kotlinx.coroutines.flow.flow {
                emit(logRepository.analyzeSince(minerId, w))
            }
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            hi3.hashkit.domain.logs.LogAnalyzer.analyze(emptyList()),
        )

    private var job: Job? = null
    private val pending = java.util.Collections.synchronizedList(mutableListOf<String>())

    init {
        connect()
        // Flush captured lines to storage on a light cadence.
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(2000)
                flushPending()
            }
        }
    }

    private suspend fun flushPending() {
        val batch: List<String>
        synchronized(pending) {
            if (pending.isEmpty()) return
            batch = pending.toList(); pending.clear()
        }
        runCatching { logRepository.record(minerId, batch) }
    }

    private fun connect() {
        job?.cancel()
        job = viewModelScope.launch {
            val entity = repository.observeMinerEntity(minerId).first() ?: return@launch
            _state.value = _state.value.copy(minerName = entity.name, status = "Connecting…")
            logStream.stream(MinerHost(entity.host, entity.port)).collect { event ->
                when (event) {
                    is EspMinerLogStream.LogEvent.Line -> {
                        pending.add(event.text) // always captured, even while paused
                        if (!_state.value.paused) {
                            _state.value = _state.value.copy(
                                status = "Live",
                                lines = (_state.value.lines + event.text).takeLast(MAX_LINES),
                            )
                        }
                    }
                    is EspMinerLogStream.LogEvent.Closed ->
                        _state.value = _state.value.copy(status = "Disconnected: ${event.reason}")
                }
            }
        }
    }

    fun togglePause() {
        _state.value = _state.value.copy(paused = !_state.value.paused)
    }

    fun clear() {
        _state.value = _state.value.copy(lines = emptyList())
    }

    fun setWindow(ms: Long) { windowMs.value = ms }

    fun reconnect() = connect()

    /** Export the captured logs for the current window (or ~24h for the live view) to a file. */
    fun exportLogs(onReady: (android.content.Intent) -> Unit) {
        viewModelScope.launch {
            flushPending()
            val w = windowMs.value.takeIf { it > 0 } ?: 86_400_000L
            val texts = runCatching { logRepository.textsSince(minerId, w) }.getOrDefault(emptyList())
            val dir = java.io.File(appContext.cacheDir, "exports").apply { mkdirs() }
            val file = java.io.File(dir, "hashkit-logs-${minerId}.txt")
            runCatching { file.writeText(texts.joinToString("\n")) }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                appContext, "${appContext.packageName}.files", file,
            )
            onReady(
                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }
    }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        const val MAX_LINES = 400
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val advancedUnlocked by viewModel.advancedUnlocked.collectAsStateWithLifecycle()
    var analyze by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Follow the tail unless paused.
    LaunchedEffect(state.lines.size, state.paused) {
        if (!state.paused && state.lines.isNotEmpty()) {
            listState.animateScrollToItem(state.lines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${state.minerName} logs", fontWeight = FontWeight.Bold)
                        Text(
                            state.status + "  ·  wallets redacted",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.status == "Live") HiBrand.statusOnline else HiBrand.textSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (advancedUnlocked) {
                        IconButton(onClick = { analyze = !analyze }) {
                            Icon(
                                Icons.Filled.Analytics,
                                contentDescription = if (analyze) "Show raw log" else "Analyze log",
                                tint = if (analyze) HiBrand.accent else HiBrand.textSecondary,
                            )
                        }
                    }
                    IconButton(onClick = viewModel::togglePause) {
                        Icon(
                            if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (state.paused) "Resume" else "Pause",
                        )
                    }
                    IconButton(onClick = viewModel::clear) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        containerColor = HiBrand.background,
    ) { padding ->
        if (state.status.startsWith("Disconnected")) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(state.status, color = HiBrand.statusDegraded)
                androidx.compose.material3.OutlinedButton(onClick = viewModel::reconnect) {
                    Text("Reconnect")
                }
            }
            return@Scaffold
        }
        if (analyze) {
            LogAnalysisPanel(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(HiBrand.background),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(state.lines) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = when {
                        line.startsWith("E ") || line.contains(" E (") -> HiBrand.statusOffline
                        line.startsWith("W ") || line.contains(" W (") -> HiBrand.statusDegraded
                        else -> HiBrand.textSecondary
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun LogAnalysisPanel(viewModel: LogsViewModel, modifier: Modifier = Modifier) {
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val window by viewModel.windowMs.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(
        modifier = modifier.background(HiBrand.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // Window selector: Live in-memory buffer, or stored history windows.
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    0L to "Live", 3_600_000L to "1h", 86_400_000L to "24h", 604_800_000L to "7d",
                ).forEach { (ms, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = window == ms,
                        onClick = { viewModel.setWindow(ms) },
                        label = { Text(label) },
                    )
                }
            }
        }
        item {
            Text(
                "LOG ANALYSIS  ·  ${analysis.summary.total} lines" +
                    "  ·  ${analysis.summary.errors} errors  ·  ${analysis.summary.warnings} warnings",
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
        }
        if (analysis.summary.byCategory.isNotEmpty()) {
            item {
                Text(
                    analysis.summary.byCategory.entries
                        .sortedByDescending { it.value }
                        .joinToString("   ") { "${it.key.label} ${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }
        }
        item {
            Text("FINDINGS", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
        }
        items(analysis.findings) { f ->
            val color = when (f.level) {
                hi3.hashkit.domain.logs.LogAnalyzer.FindingLevel.ERROR -> HiBrand.statusOffline
                hi3.hashkit.domain.logs.LogAnalyzer.FindingLevel.WARN -> HiBrand.statusDegraded
                hi3.hashkit.domain.logs.LogAnalyzer.FindingLevel.INFO -> HiBrand.statusOnline
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(f.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
                    Text(f.suggestion, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                }
            }
        }
        item {
            androidx.compose.material3.OutlinedButton(onClick = {
                viewModel.exportLogs { intent ->
                    ctx.startActivity(android.content.Intent.createChooser(intent, "Export logs"))
                }
            }) { Text("Export captured logs") }
        }
        item {
            Text(
                "Heuristic, on-device analysis — no cloud. Live analyzes the current stream; " +
                    "1h/24h/7d analyze captured history (kept per miner, ~7 days). Wallets redacted.",
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
        }
    }
}
