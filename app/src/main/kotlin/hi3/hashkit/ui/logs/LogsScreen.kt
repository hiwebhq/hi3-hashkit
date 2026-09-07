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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import kotlinx.coroutines.flow.first
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
    private val repository: MinerRepository,
    private val logStream: EspMinerLogStream,
) : ViewModel() {

    private val minerId: Long = checkNotNull(savedStateHandle["minerId"])
    private val _state = MutableStateFlow(LogsUiState())
    val state = _state

    private var job: Job? = null

    init {
        connect()
    }

    private fun connect() {
        job?.cancel()
        job = viewModelScope.launch {
            val entity = repository.observeMinerEntity(minerId).first() ?: return@launch
            _state.value = _state.value.copy(minerName = entity.name, status = "Connecting…")
            logStream.stream(MinerHost(entity.host, entity.port)).collect { event ->
                when (event) {
                    is EspMinerLogStream.LogEvent.Line -> {
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

    fun reconnect() = connect()

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
