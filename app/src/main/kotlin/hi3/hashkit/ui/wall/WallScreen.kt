package hi3.hashkit.ui.wall

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.core.Units
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.ui.rack.RackGroup
import hi3.hashkit.ui.rack.RackViewModel
import hi3.hashkit.ui.theme.HiBrand

/** Selectable Wall / TV tile+font size, chosen on the wall page. */
enum class WallSize(val label: String) {
    SMALL("Small"), MEDIUM("Medium"), LARGE("Large");

    companion object {
        fun fromName(name: String?): WallSize = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/** Concrete dimensions for each [WallSize]. */
data class WallDims(
    val tileWidth: Dp, val tilePad: Dp, val tileGap: Dp, val dot: Dp,
    val nameSp: TextUnit, val hashSp: TextUnit, val tempSp: TextUnit,
    val totalSp: TextUnit, val countsSp: TextUnit, val groupSp: TextUnit,
    val contentPad: Dp, val sectionGap: Dp,
)

fun wallDims(size: WallSize): WallDims = when (size) {
    WallSize.SMALL -> WallDims(
        tileWidth = 180.dp, tilePad = 14.dp, tileGap = 12.dp, dot = 14.dp,
        nameSp = 18.sp, hashSp = 26.sp, tempSp = 14.sp,
        totalSp = 44.sp, countsSp = 16.sp, groupSp = 20.sp,
        contentPad = 20.dp, sectionGap = 18.dp,
    )
    WallSize.MEDIUM -> WallDims(
        tileWidth = 240.dp, tilePad = 18.dp, tileGap = 16.dp, dot = 18.dp,
        nameSp = 22.sp, hashSp = 34.sp, tempSp = 18.sp,
        totalSp = 56.sp, countsSp = 20.sp, groupSp = 24.sp,
        contentPad = 28.dp, sectionGap = 22.dp,
    )
    WallSize.LARGE -> WallDims(
        tileWidth = 320.dp, tilePad = 24.dp, tileGap = 20.dp, dot = 22.dp,
        nameSp = 28.sp, hashSp = 44.sp, tempSp = 22.sp,
        totalSp = 72.sp, countsSp = 24.sp, groupSp = 30.sp,
        contentPad = 32.dp, sectionGap = 28.dp,
    )
}

/**
 * Kiosk / wall-dashboard view: a full-screen, glanceable fleet board meant for a spare
 * phone, tablet, or Android TV left on the shelf. Keeps the screen awake while shown and
 * auto-updates from the same poll cycle as the dashboard. Read-only — no controls here.
 */
@Composable
fun WallScreen(
    onExit: () -> Unit,
    viewModel: RackViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val fahrenheit by viewModel.useFahrenheit.collectAsStateWithLifecycle()
    val wallSize by viewModel.wallSize.collectAsStateWithLifecycle()
    val wallColumns by viewModel.wallColumns.collectAsStateWithLifecycle()
    val dims = wallDims(wallSize)
    var optionsOpen by remember { mutableStateOf(false) }

    // Keep the screen on while the wall is up; clear the flag when leaving.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val allMiners = groups.flatMap { it.miners }
    val online = allMiners.count { it.status == MinerStatus.ONLINE }
    val offline = allMiners.count { it.status == MinerStatus.OFFLINE }
    val totalHash = allMiners
        .filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
        .sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }

    BoxWithConstraints(Modifier.fillMaxSize().background(HiBrand.background)) {
        // When a fixed column count is set, size each tile so exactly that many fill the width;
        // 0 = auto (tiles keep their per-size width and wrap).
        val tileWidth: Dp = if (wallColumns in 1..8) {
            ((maxWidth - dims.contentPad * 2 - dims.tileGap * (wallColumns - 1)) / wallColumns)
                .coerceAtLeast(64.dp)
        } else {
            dims.tileWidth
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(dims.contentPad),
            verticalArrangement = Arrangement.spacedBy(dims.sectionGap),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            Units.formatHashrate(totalHash),
                            fontSize = dims.totalSp,
                            fontWeight = FontWeight.Bold,
                            color = HiBrand.textPrimary,
                        )
                        Text(
                            "$online online · $offline offline · ${allMiners.size} miners",
                            fontSize = dims.countsSp,
                            color = HiBrand.textSecondary,
                        )
                    }
                    // Compact controls: a single gear opens a popup with size + grid; then Exit.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { optionsOpen = true }, modifier = Modifier.size(56.dp)) {
                                Icon(
                                    Icons.Filled.Tune,
                                    contentDescription = "Display options",
                                    tint = HiBrand.textSecondary,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text("CARD SIZE", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                                    Row(Modifier.padding(top = 4.dp)) {
                                        WallSize.entries.forEach { s ->
                                            FilterChip(
                                                selected = s == wallSize,
                                                onClick = { viewModel.setWallSize(s) },
                                                label = { Text(s.label) },
                                                modifier = Modifier.padding(end = 6.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        "GRID COLUMNS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HiBrand.textSecondary,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                        IconButton(onClick = { viewModel.setWallColumns((wallColumns - 1).coerceAtLeast(0)) }) {
                                            Icon(Icons.Filled.Remove, contentDescription = "Fewer columns", tint = HiBrand.textPrimary)
                                        }
                                        Text(
                                            if (wallColumns == 0) "Auto" else "$wallColumns",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = HiBrand.textPrimary,
                                            modifier = Modifier.width(56.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                        IconButton(onClick = { viewModel.setWallColumns((wallColumns + 1).coerceAtMost(8)) }) {
                                            Icon(Icons.Filled.Add, contentDescription = "More columns", tint = HiBrand.textPrimary)
                                        }
                                    }
                                }
                            }
                        }
                        IconButton(onClick = onExit, modifier = Modifier.size(56.dp)) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Exit wall mode",
                                tint = HiBrand.textSecondary,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
            }
            if (allMiners.isEmpty()) {
                item {
                    Text(
                        "No miners to display.",
                        style = MaterialTheme.typography.titleLarge,
                        color = HiBrand.textSecondary,
                    )
                }
            }
            for (group in groups) {
                item(key = "wall-${group.location}") {
                    Column {
                        Text(
                            "${group.location}  ·  ${group.online}/${group.total}",
                            fontSize = dims.groupSp,
                            fontWeight = FontWeight.SemiBold,
                            color = HiBrand.textPrimary,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dims.tileGap),
                            verticalArrangement = Arrangement.spacedBy(dims.tileGap),
                        ) {
                            group.miners.forEach { WallTile(it, fahrenheit, dims, tileWidth) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallTile(miner: Miner, fahrenheit: Boolean, dims: WallDims, tileWidth: Dp = dims.tileWidth) {
    val color = when (miner.status) {
        MinerStatus.ONLINE -> HiBrand.statusOnline
        MinerStatus.DEGRADED -> HiBrand.statusDegraded
        MinerStatus.OFFLINE -> HiBrand.statusOffline
        MinerStatus.UNKNOWN -> HiBrand.statusUnknown
    }
    Column(
        modifier = Modifier
            .width(tileWidth)
            .clip(RoundedCornerShape(18.dp))
            .background(HiBrand.surface)
            .padding(dims.tilePad),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(dims.dot).clip(CircleShape).background(color))
            Text(
                miner.name,
                fontSize = dims.nameSp,
                fontWeight = FontWeight.Bold,
                color = HiBrand.textPrimary,
                maxLines = 1,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
        val hr = miner.lastTelemetry?.hashrateGhs?.value
        Text(
            if (miner.status == MinerStatus.OFFLINE) "Offline" else Units.formatHashrate(hr),
            fontSize = dims.hashSp,
            fontWeight = FontWeight.Bold,
            color = if (miner.status == MinerStatus.OFFLINE) HiBrand.statusOffline else HiBrand.textPrimary,
        )
        val temp = miner.lastTelemetry?.chipTempC?.value
        Text(
            temp?.let { Units.formatTemp(it, fahrenheit) } ?: "—",
            fontSize = dims.tempSp,
            color = HiBrand.textSecondary,
        )
    }
}
