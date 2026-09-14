package hi3.hashkit.ui.wall

import android.app.Activity
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.R
import hi3.hashkit.core.Units
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.ui.rack.RackGroup
import hi3.hashkit.ui.rack.RackViewModel
import hi3.hashkit.ui.sitemap.Slot
import hi3.hashkit.ui.theme.HiBrand

/** Selectable Wall / TV tile+font size, chosen on the wall page. */
enum class WallSize(@StringRes val labelRes: Int) {
    SMALL(R.string.wall_size_small),
    MEDIUM(R.string.wall_size_medium),
    LARGE(R.string.wall_size_large);

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
    val rawGroups by viewModel.groups.collectAsStateWithLifecycle()
    val fahrenheit by viewModel.useFahrenheit.collectAsStateWithLifecycle()
    val wallSize by viewModel.wallSize.collectAsStateWithLifecycle()
    val wallColumns by viewModel.wallColumns.collectAsStateWithLifecycle()
    val showTrend by viewModel.wallShowTrend.collectAsStateWithLifecycle()
    val sparklines by viewModel.wallSparklines.collectAsStateWithLifecycle()
    val dims = wallDims(wallSize)
    var optionsOpen by remember { mutableStateOf(false) }

    // Site-Map slot codes (B1-R1-T1-P1) are per-miner, which exploded the wall into one
    // group — and so one tile row — per machine. Merge groups up to rack level (B1-R1) so
    // the tiles actually tile; miners inside sort in physical order (tier, then position).
    val groups = remember(rawGroups) {
        rawGroups
            .groupBy { g -> Slot.parse(g.location)?.let { "B${it.building}-R${it.rack}" } ?: g.location }
            .map { (loc, gs) ->
                RackGroup(
                    loc,
                    gs.flatMap { it.miners }.sortedWith(
                        compareBy(
                            { Slot.parse(it.location) == null },
                            { Slot.parse(it.location)?.tier ?: 0 },
                            { Slot.parse(it.location)?.position ?: 0 },
                            { it.name.lowercase() },
                        ),
                    ),
                )
            }
    }

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
                            stringResource(R.string.wall_counts, online, offline, allMiners.size),
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
                                    contentDescription = stringResource(R.string.wall_display_options),
                                    tint = HiBrand.textSecondary,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        stringResource(R.string.wall_card_size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HiBrand.textSecondary,
                                    )
                                    Row(Modifier.padding(top = 4.dp)) {
                                        WallSize.entries.forEach { s ->
                                            FilterChip(
                                                selected = s == wallSize,
                                                onClick = { viewModel.setWallSize(s) },
                                                label = { Text(stringResource(s.labelRes)) },
                                                modifier = Modifier.padding(end = 6.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        stringResource(R.string.wall_grid_columns),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HiBrand.textSecondary,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                        IconButton(onClick = { viewModel.setWallColumns((wallColumns - 1).coerceAtLeast(0)) }) {
                                            Icon(
                                                Icons.Filled.Remove,
                                                contentDescription = stringResource(R.string.wall_fewer_columns),
                                                tint = HiBrand.textPrimary,
                                            )
                                        }
                                        Text(
                                            if (wallColumns == 0) stringResource(R.string.wall_columns_auto) else "$wallColumns",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = HiBrand.textPrimary,
                                            modifier = Modifier.width(56.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                        IconButton(onClick = { viewModel.setWallColumns((wallColumns + 1).coerceAtMost(8)) }) {
                                            Icon(
                                                Icons.Filled.Add,
                                                contentDescription = stringResource(R.string.wall_more_columns),
                                                tint = HiBrand.textPrimary,
                                            )
                                        }
                                    }
                                    Row(Modifier.padding(top = 12.dp)) {
                                        FilterChip(
                                            selected = showTrend,
                                            onClick = { viewModel.setWallShowTrend(!showTrend) },
                                            label = { Text(stringResource(R.string.wall_trend_1h)) },
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(onClick = onExit, modifier = Modifier.size(56.dp)) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.wall_exit),
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
                        stringResource(R.string.wall_no_miners),
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
                            group.miners.forEach {
                                WallTile(
                                    it, fahrenheit, dims, tileWidth,
                                    trend = if (showTrend) sparklines[it.id] else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("MagicNumber") // tile geometry/scale literals ARE the layout
@Composable
private fun WallTile(
    miner: Miner,
    fahrenheit: Boolean,
    dims: WallDims,
    tileWidth: Dp = dims.tileWidth,
    trend: List<Double>? = null,
) {
    val color = when (miner.status) {
        MinerStatus.ONLINE -> HiBrand.statusOnline
        MinerStatus.DEGRADED -> HiBrand.statusDegraded
        MinerStatus.OFFLINE -> HiBrand.statusOffline
        MinerStatus.UNKNOWN -> HiBrand.statusUnknown
    }
    // A forced column count can squeeze tiles well below the preset width — shrink the
    // type with the tile so text never clips.
    val fontScale = (tileWidth / dims.tileWidth).coerceIn(0.55f, 1f)
    Column(
        modifier = Modifier
            .width(tileWidth)
            .clip(RoundedCornerShape(18.dp))
            .background(HiBrand.surface)
            .padding(dims.tilePad * fontScale),
        verticalArrangement = Arrangement.spacedBy(8.dp * fontScale),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(dims.dot * fontScale).clip(CircleShape).background(color))
            Text(
                miner.name,
                fontSize = dims.nameSp * fontScale,
                fontWeight = FontWeight.Bold,
                color = HiBrand.textPrimary,
                maxLines = 1,
                modifier = Modifier.padding(start = 14.dp * fontScale),
            )
        }
        val hr = miner.lastTelemetry?.hashrateGhs?.value
        Text(
            if (miner.status == MinerStatus.OFFLINE) stringResource(R.string.status_offline) else Units.formatHashrate(hr),
            fontSize = dims.hashSp * fontScale,
            fontWeight = FontWeight.Bold,
            color = if (miner.status == MinerStatus.OFFLINE) HiBrand.statusOffline else HiBrand.textPrimary,
        )
        if (trend != null && trend.size >= 2) {
            TrendLine(
                trend,
                height = (tileWidth * 0.16f).coerceIn(18.dp, 44.dp),
                color = if (miner.status == MinerStatus.OFFLINE) HiBrand.statusOffline else HiBrand.accent,
            )
        }
        val temp = miner.lastTelemetry?.chipTempC?.value
        Text(
            temp?.let { Units.formatTemp(it, fahrenheit) } ?: "—",
            fontSize = dims.tempSp * fontScale,
            color = HiBrand.textSecondary,
        )
    }
}

/** Minimal last-1h hashrate sparkline: a single scaled polyline, no axes. */
@Suppress("MagicNumber") // a Canvas renderer: the geometry literals ARE the drawing
@Composable
private fun TrendLine(points: List<Double>, height: Dp, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(height)) {
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
        val stepX = size.width / (points.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            // 10% headroom top and bottom so a flat line doesn't hug an edge.
            val y = size.height * (0.9f - 0.8f * ((v - min) / span).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path, color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}
