package hi3.hashkit.ui.sitemap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.R
import hi3.hashkit.data.poll.PollingEngine
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.ui.theme.HiBrand
import hi3.hashkit.ui.theme.TempColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt

/** One placed miner on the heatmap. */
data class HeatCell(
    val minerId: Long,
    val name: String,
    val slot: Slot,
    val chipTempC: Double?,
    val status: MinerStatus,
)

data class SiteHeatState(
    /** Geometry re-derived from saved location codes; null = nothing placed yet. */
    val config: SiteMapConfig? = null,
    val cells: Map<Slot, HeatCell> = emptyMap(),
    /** Miners whose Location isn't a B/R/T/P code — shown as a hint, not an error. */
    val unplaced: Int = 0,
    val useFahrenheit: Boolean = false,
)

@HiltViewModel
class SiteHeatmapViewModel @Inject constructor(
    repository: MinerRepository,
    pollingEngine: PollingEngine,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<SiteHeatState> =
        combine(
            repository.observeMinerEntities(), pollingEngine.lastRefresh, settingsRepository.settings,
        ) { entities, _, settings ->
            val now = Instant.now()
            val miners = entities
                .filter { settings.demoModeEnabled || !it.isDemo }
                .map { repository.toDomain(it, now) }
            val cells = miners.mapNotNull { m ->
                val slot = Slot.parse(m.location) ?: return@mapNotNull null
                HeatCell(m.id, m.name, slot, m.lastTelemetry?.chipTempC?.value, m.status)
            }
            SiteHeatState(
                config = SiteWalk.geometryOf(cells.map { it.slot }),
                // On duplicate codes the last write wins; the count still shows in the header.
                cells = cells.associateBy { it.slot },
                unplaced = miners.size - cells.size,
                useFahrenheit = settings.useFahrenheit,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SiteHeatState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteHeatmapScreen(
    onMinerClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SiteHeatmapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.site_heatmap_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        containerColor = HiBrand.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val cfg = state.config
            if (cfg == null) {
                Text(
                    stringResource(R.string.site_heatmap_empty),
                    style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary,
                )
            } else {
                HeatRackPager(state, cfg, onMinerClick)
                Legend(state.useFahrenheit)
                if (state.unplaced > 0) {
                    Text(
                        stringResource(R.string.site_unplaced, state.unplaced),
                        style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatRackPager(state: SiteHeatState, cfg: SiteMapConfig, onMinerClick: (Long) -> Unit) {
    val totalRacks = cfg.buildings * cfg.racksPerBuilding
    var viewed by rememberSaveable { mutableIntStateOf(firstOccupiedRack(state, cfg)) }
    val building = viewed / cfg.racksPerBuilding + 1
    val rack = viewed % cfg.racksPerBuilding + 1
    Card(colors = CardDefaults.cardColors(containerColor = HiBrand.surface), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { if (viewed > 0) viewed-- }, enabled = viewed > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.site_prev_rack))
                }
                Text(
                    stringResource(R.string.site_building_rack, building, rack),
                    style = MaterialTheme.typography.titleSmall, color = HiBrand.textPrimary,
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { if (viewed < totalRacks - 1) viewed++ }, enabled = viewed < totalRacks - 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.site_next_rack))
                }
            }
            HeatRackGrid(state, cfg, building, rack, onMinerClick)
            Text(
                stringResource(R.string.site_heatmap_tier_hint),
                style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary,
            )
        }
    }
}

/** Start on the first rack that actually holds a miner (geometry can start above rack 1). */
private fun firstOccupiedRack(state: SiteHeatState, cfg: SiteMapConfig): Int =
    state.cells.keys.minOfOrNull { (it.building - 1) * cfg.racksPerBuilding + (it.rack - 1) } ?: 0

@Composable
private fun HeatRackGrid(
    state: SiteHeatState,
    cfg: SiteMapConfig,
    building: Int,
    rack: Int,
    onMinerClick: (Long) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        for (tier in cfg.tiersPerRack downTo 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "T$tier", style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary, modifier = Modifier.width(24.dp),
                )
                for (position in 1..cfg.positionsPerTier) {
                    HeatCellBox(state.cells[Slot(building, rack, tier, position)], state.useFahrenheit, onMinerClick)
                }
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod") // one branch per cell state/threshold band
@Composable
private fun HeatCellBox(cell: HeatCell?, fahrenheit: Boolean, onMinerClick: (Long) -> Unit) {
    val live = cell != null &&
        (cell.status == MinerStatus.ONLINE || cell.status == MinerStatus.DEGRADED) &&
        cell.chipTempC != null
    val bg = if (live) TempColors.tempColor(cell?.chipTempC) else HiBrand.surface
    val fg = when {
        live -> HiBrand.background
        cell?.status == MinerStatus.OFFLINE -> HiBrand.statusOffline
        else -> HiBrand.textSecondary
    }
    val label = when {
        live -> formatCellTemp(cell?.chipTempC, fahrenheit)
        cell == null -> "·"
        cell.status == MinerStatus.OFFLINE -> stringResource(R.string.site_cell_off)
        else -> "—" // stale/unknown telemetry: never pretend a temperature
    }
    val desc = cell?.let {
        "${it.name}: ${if (live) formatCellTemp(it.chipTempC, fahrenheit) else it.status.name.lowercase()}"
    } ?: stringResource(R.string.site_empty_slot)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 44.dp, height = 36.dp)
            .background(bg, RoundedCornerShape(6.dp))
            .border(
                1.dp,
                if (cell != null) HiBrand.outline else HiBrand.outline.copy(alpha = 0.4f),
                RoundedCornerShape(6.dp),
            )
            .let { m -> if (cell != null) m.clickable { onMinerClick(cell.minerId) } else m }
            .semantics { contentDescription = desc },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium, color = fg,
            fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Legend(fahrenheit: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        LegendSwatch(HiBrand.accentAlt, "< ${band(TempColors.WARM_C, fahrenheit)}")
        LegendSwatch(TempColors.warm, "${band(TempColors.WARM_C, fahrenheit)}+")
        LegendSwatch(HiBrand.statusDegraded, "${band(TempColors.HOT_C, fahrenheit)}+")
        LegendSwatch(HiBrand.statusOffline, "${band(TempColors.CRITICAL_C, fahrenheit)}+")
        LegendSwatch(HiBrand.surface, stringResource(R.string.site_legend_offline))
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp))
                .border(1.dp, HiBrand.outline, RoundedCornerShape(3.dp)),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
        Spacer(Modifier.width(0.dp))
    }
}

@Suppress("MagicNumber") // the Celsius→Fahrenheit formula is what it is
private fun cToF(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

private fun band(celsius: Double, fahrenheit: Boolean): String =
    if (fahrenheit) "${cToF(celsius).roundToInt()}°F" else "${celsius.roundToInt()}°C"

private fun formatCellTemp(celsius: Double?, fahrenheit: Boolean): String {
    if (celsius == null) return "—"
    val v = if (fahrenheit) cToF(celsius) else celsius
    return "${v.roundToInt()}°"
}
