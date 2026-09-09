package hi3.hashkit.ui.rack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.core.Units
import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

/** A location (site/room/rack) and the miners placed in it, plus its rolled-up status. */
data class RackGroup(
    val location: String,
    val miners: List<Miner>,
) {
    val online: Int get() = miners.count { it.status == MinerStatus.ONLINE }
    val total: Int get() = miners.size
    val hashrateGhs: Double
        get() = miners
            .filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
            .sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }
}

@HiltViewModel
class RackViewModel @Inject constructor(
    private val repository: MinerRepository,
    private val pollingEngine: PollingEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val useFahrenheit: StateFlow<Boolean> =
        settingsRepository.settings
            .map { it.useFahrenheit }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val groups: StateFlow<List<RackGroup>> =
        combine(
            repository.observeMinerEntities(),
            pollingEngine.lastRefresh,
            settingsRepository.settings,
        ) { entities, _, settings ->
            val now = Instant.now()
            val miners = entities
                .filter { settings.demoModeEnabled || !it.isDemo }
                .map { repository.toDomain(it, now) }
            groupByLocation(miners)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        private const val UNASSIGNED = "Unassigned"

        /** Group by location (blank → Unassigned), locations alphabetical, Unassigned last. */
        fun groupByLocation(miners: List<Miner>): List<RackGroup> =
            miners
                .groupBy { it.location?.trim()?.takeIf { l -> l.isNotEmpty() } ?: UNASSIGNED }
                .map { (loc, list) -> RackGroup(loc, list.sortedBy { it.name.lowercase() }) }
                .sortedWith(
                    compareBy({ it.location == UNASSIGNED }, { it.location.lowercase() }),
                )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RackScreen(
    onBack: () -> Unit,
    onMinerClick: (Long) -> Unit,
    viewModel: RackViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val fahrenheit by viewModel.useFahrenheit.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rack & site layout", fontWeight = FontWeight.Bold) },
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
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No miners yet. Add miners and set each one's Location " +
                        "(room / rack / shelf) to see them arranged here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HiBrand.textSecondary,
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            for (group in groups) {
                item(key = "hdr-${group.location}") {
                    Column {
                        RackHeader(group)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            group.miners.forEach { miner ->
                                MinerTile(miner, fahrenheit, onClick = { onMinerClick(miner.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RackHeader(group: RackGroup) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.location,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = HiBrand.textPrimary,
        )
        Text(
            "${group.online}/${group.total} up · ${Units.formatHashrate(group.hashrateGhs)}",
            style = MaterialTheme.typography.bodySmall,
            color = HiBrand.textSecondary,
        )
    }
}

@Composable
private fun MinerTile(miner: Miner, fahrenheit: Boolean, onClick: () -> Unit) {
    val statusColor = statusColor(miner.status)
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HiBrand.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(statusColor),
            )
            Text(
                miner.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = HiBrand.textPrimary,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        val hr = miner.lastTelemetry?.hashrateGhs?.value
        Text(
            if (miner.status == MinerStatus.OFFLINE) "Offline" else Units.formatHashrate(hr),
            style = MaterialTheme.typography.bodyMedium,
            color = if (miner.status == MinerStatus.OFFLINE) HiBrand.statusOffline else HiBrand.textPrimary,
        )
        val temp = miner.lastTelemetry?.chipTempC?.value
        Text(
            listOfNotNull(
                miner.identity.model?.takeIf { it.isNotBlank() },
                temp?.let { Units.formatTemp(it, fahrenheit) },
            ).joinToString(" · ").ifEmpty { "—" },
            style = MaterialTheme.typography.labelSmall,
            color = HiBrand.textSecondary,
            maxLines = 1,
        )
    }
}

private fun statusColor(status: MinerStatus): Color = when (status) {
    MinerStatus.ONLINE -> HiBrand.statusOnline
    MinerStatus.DEGRADED -> HiBrand.statusDegraded
    MinerStatus.OFFLINE -> HiBrand.statusOffline
    MinerStatus.UNKNOWN -> HiBrand.statusUnknown
}
