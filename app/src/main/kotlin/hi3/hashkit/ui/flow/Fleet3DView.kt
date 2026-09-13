@file:Suppress("MagicNumber") // a Canvas renderer: the geometry/palette literals ARE the drawing

package hi3.hashkit.ui.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import hi3.hashkit.core.Units
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.viz.Fleet3D
import hi3.hashkit.ui.theme.HiBrand

private const val YAW_START = 0.55f
private const val PITCH_START = 0.35f
private const val PITCH_MAX = 1.35f
private const val ROTATE_PER_PX = 0.008f
private const val ZOOM_MIN = 0.3f
private const val ZOOM_MAX = 6f
private const val PX_PER_UNIT_FRACTION = 0.055f
private const val TAP_RADIUS_PX = 60f
private const val FLIR_MIN_C = 25f
private const val FLIR_MAX_C = 85f
private const val BLOOM_HEAT = 0.55f

/** The 3D fleet view: orbit/zoom scene of the fleet with an optional FLIR thermal mode. */
@Composable
fun Fleet3DView(viewModel: Fleet3DViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var layoutMode by remember { mutableStateOf(Fleet3D.Layout.RACKS) }
    var flir by remember { mutableStateOf(false) }
    var whiteHot by remember { mutableStateOf(false) }
    var yaw by remember { mutableFloatStateOf(YAW_START) }
    var pitch by remember { mutableFloatStateOf(PITCH_START) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    val scene = remember(state.units, layoutMode, state.rackSize) {
        Fleet3D.layout(
            state.units.map { Fleet3D.Unit3D(it.locationCode, Fleet3D.ipKey(it.host), it.farmOrdinal) },
            layoutMode,
            state.rackSize,
        )
    }
    val pivot = remember(scene) { Fleet3D.centerOf(scene.placed) }
    // Screen positions of the last draw, for tap hit-testing.
    val hitCenters = remember { mutableMapOf<Long, Offset>() }

    Column(Modifier.fillMaxSize()) {
        Fleet3DControls(
            layoutMode = layoutMode,
            onLayout = { layoutMode = it },
            flir = flir,
            onFlir = { flir = it },
            whiteHot = whiteHot,
            onWhiteHot = { whiteHot = it },
            rackSize = state.rackSize,
            onRackSize = viewModel::setRackSize,
        )
        Box(Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            yaw += pan.x * ROTATE_PER_PX
                            pitch = (pitch + pan.y * ROTATE_PER_PX).coerceIn(-PITCH_MAX, PITCH_MAX)
                            zoom = (zoom * gestureZoom).coerceIn(ZOOM_MIN, ZOOM_MAX)
                        }
                    }
                    .pointerInput(scene) {
                        detectTapGestures { tap ->
                            selectedId = hitCenters.entries
                                .filter { (tap - it.value).getDistance() < TAP_RADIUS_PX }
                                .minByOrNull { (tap - it.value).getDistance() }?.key
                        }
                    },
            ) {
                drawScene(
                    scene, state.units, pivot, yaw, pitch, zoom,
                    flir, whiteHot, hitCenters, selectedId,
                )
            }
            state.units.firstOrNull { it.id == selectedId }?.let { SelectedReadout(it, flir) }
            if (flir) FlirLegend(whiteHot)
        }
    }
}

@Composable
private fun Fleet3DControls(
    layoutMode: Fleet3D.Layout,
    onLayout: (Fleet3D.Layout) -> Unit,
    flir: Boolean,
    onFlir: (Boolean) -> Unit,
    whiteHot: Boolean,
    onWhiteHot: (Boolean) -> Unit,
    rackSize: Int,
    onRackSize: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
    ) {
        FilterChip(
            selected = layoutMode == Fleet3D.Layout.RACKS,
            onClick = { onLayout(Fleet3D.Layout.RACKS) }, label = { Text("Racks") },
        )
        FilterChip(
            selected = layoutMode == Fleet3D.Layout.FARMS,
            onClick = { onLayout(Fleet3D.Layout.FARMS) }, label = { Text("Farms") },
        )
        FilterChip(selected = flir, onClick = { onFlir(!flir) }, label = { Text("FLIR") })
        if (flir) {
            FilterChip(
                selected = whiteHot,
                onClick = { onWhiteHot(!whiteHot) }, label = { Text("White-hot") },
            )
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(horizontal = 10.dp),
    ) {
        Text(
            "Rack ${rackSize}×$rackSize",
            style = MaterialTheme.typography.labelSmall,
            color = HiBrand.textSecondary,
        )
        TextButton(
            onClick = { onRackSize(rackSize - 1) },
            enabled = rackSize > Fleet3D.RACK_SIZE_MIN,
        ) { Text("–") }
        TextButton(
            onClick = { onRackSize(rackSize + 1) },
            enabled = rackSize < Fleet3D.RACK_SIZE_MAX,
        ) { Text("+") }
        Text(
            "drag to rotate · pinch to zoom · tap a unit",
            style = MaterialTheme.typography.labelSmall,
            color = HiBrand.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Suppress("LongParameterList") // a render call is inherently the whole camera + scene state
private fun DrawScope.drawScene(
    scene: Fleet3D.Scene,
    units: List<Unit3DUi>,
    pivot: Fleet3D.P3,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    flir: Boolean,
    whiteHot: Boolean,
    hitCenters: MutableMap<Long, Offset>,
    selectedId: Long?,
) {
    drawRect(if (flir) Color.Black else HiBrand.background)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val ppu = size.minDimension * PX_PER_UNIT_FRACTION
    fun proj(p: Fleet3D.P3) = Fleet3D.project(p, pivot, yaw, pitch, zoom, cx, cy, ppu)

    drawFrames(scene.frames, flir, ::proj)

    hitCenters.clear()
    val hottest = units.filter { it.chipTempC != null }.maxByOrNull { it.chipTempC!! }
    scene.placed
        .map { it to proj(it.center) }
        .sortedBy { it.second.depth } // far first (painter's algorithm)
        .forEach { (placedBox, c) ->
            val unit = units.getOrNull(placedBox.index) ?: return@forEach
            val half = Fleet3D.BOX / 2f
            val corners = buildList {
                for (dz in listOf(-half, half)) for (dy in listOf(-half, half)) for (dx in listOf(-half, half)) {
                    add(
                        proj(
                            Fleet3D.P3(
                                placedBox.center.x + dx,
                                placedBox.center.y + dy,
                                placedBox.center.z + dz,
                            )
                        )
                    )
                }
            }
            val heat = heatOf(unit)
            if (flir && heat > BLOOM_HEAT) {
                drawCircle(
                    color = flirColor(heat, whiteHot).copy(alpha = 0.30f),
                    radius = Fleet3D.BOX * ppu * c.scale * 1.4f,
                    center = Offset(c.x, c.y),
                )
            }
            drawUnitBox(corners, unit, heat, flir, whiteHot, unit.id == selectedId)
            hitCenters[unit.id] = Offset(c.x, c.y)
            if (flir && unit == hottest) drawCrosshair(Offset(c.x, c.y), unit.chipTempC)
        }
}

/** Faces as corner-index quads (corner order: x fastest, then y, then z). */
private val FACES = listOf(
    intArrayOf(0, 1, 3, 2), // back
    intArrayOf(4, 5, 7, 6), // front
    intArrayOf(0, 1, 5, 4), // bottom
    intArrayOf(2, 3, 7, 6), // top
    intArrayOf(0, 2, 6, 4), // left
    intArrayOf(1, 3, 7, 5), // right
)
private val FACE_SHADE = floatArrayOf(0.55f, 1.0f, 0.45f, 0.85f, 0.65f, 0.65f)

@Suppress("LongParameterList") // face renderer needs the box's full display state
private fun DrawScope.drawUnitBox(
    corners: List<Fleet3D.Projected>,
    unit: Unit3DUi,
    heat: Float,
    flir: Boolean,
    whiteHot: Boolean,
    selected: Boolean,
) {
    FACES.indices.sortedBy { f -> FACES[f].map { corners[it].depth }.average() }
        .forEach { f ->
            val quad = FACES[f]
            val shade = FACE_SHADE[f]
            val color = if (flir) {
                flirColor(heat * shade.coerceAtLeast(0.7f), whiteHot)
            } else {
                statusColor(unit.status).let {
                    Color(it.red * shade, it.green * shade, it.blue * shade)
                }
            }
            val path = Path().apply {
                moveTo(corners[quad[0]].x, corners[quad[0]].y)
                for (k in 1 until quad.size) lineTo(corners[quad[k]].x, corners[quad[k]].y)
                close()
            }
            drawPath(path, color)
            if (selected) {
                drawPath(
                    path,
                    if (flir) Color.White else HiBrand.accent,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                )
            }
        }
}

private fun DrawScope.drawFrames(
    frames: List<Fleet3D.Frame>,
    flir: Boolean,
    proj: (Fleet3D.P3) -> Fleet3D.Projected,
) {
    val color = if (flir) Color(0.25f, 0.25f, 0.25f) else HiBrand.outline
    frames.forEach { fr ->
        val c = listOf(
            Fleet3D.P3(fr.min.x, fr.min.y, fr.min.z), Fleet3D.P3(fr.max.x, fr.min.y, fr.min.z),
            Fleet3D.P3(fr.min.x, fr.max.y, fr.min.z), Fleet3D.P3(fr.max.x, fr.max.y, fr.min.z),
            Fleet3D.P3(fr.min.x, fr.min.y, fr.max.z), Fleet3D.P3(fr.max.x, fr.min.y, fr.max.z),
            Fleet3D.P3(fr.min.x, fr.max.y, fr.max.z), Fleet3D.P3(fr.max.x, fr.max.y, fr.max.z),
        ).map(proj)
        val edges = listOf(
            0 to 1, 2 to 3, 4 to 5, 6 to 7, // x edges
            0 to 2, 1 to 3, 4 to 6, 5 to 7, // y edges
            0 to 4, 1 to 5, 2 to 6, 3 to 7, // z edges
        )
        edges.forEach { (a, b) ->
            drawLine(color, Offset(c[a].x, c[a].y), Offset(c[b].x, c[b].y), strokeWidth = 2f)
        }
    }
}

private fun DrawScope.drawCrosshair(center: Offset, tempC: Double?) {
    val arm = 26f
    val gap = 12f
    val white = Color.White
    listOf(
        Offset(center.x - arm, center.y) to Offset(center.x - gap, center.y),
        Offset(center.x + gap, center.y) to Offset(center.x + arm, center.y),
        Offset(center.x, center.y - arm) to Offset(center.x, center.y - gap),
        Offset(center.x, center.y + gap) to Offset(center.x, center.y + arm),
    ).forEach { (a, b) -> drawLine(white, a, b, strokeWidth = 2.5f) }
    drawCircle(white, radius = 3f, center = center)
    tempC?.let {
        drawContext.canvas.nativeCanvas.drawText(
            "%.0f°C".format(it),
            center.x + arm + 6f,
            center.y + 8f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 30f
                isAntiAlias = true
            },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SelectedReadout(unit: Unit3DUi, flir: Boolean) {
    Column(
        Modifier
            .align(androidx.compose.ui.Alignment.TopEnd)
            .padding(10.dp),
    ) {
        Text(
            unit.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (flir) Color.White else HiBrand.textPrimary,
        )
        Text(
            unit.host + " · " + Units.formatHashrate(unit.hashrateGhs) +
                (unit.chipTempC?.let { " · %.0f°C".format(it) } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = if (flir) Color.White else HiBrand.textSecondary,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.FlirLegend(whiteHot: Boolean) {
    Row(
        Modifier
            .align(androidx.compose.ui.Alignment.BottomStart)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.padding(top = 4.dp).fillMaxWidth(0.4f).height(12.dp).padding(end = 6.dp)) {
            val steps = 40
            val w = size.width / steps
            for (s in 0 until steps) {
                drawRect(
                    flirColor(s / (steps - 1f), whiteHot),
                    topLeft = Offset(s * w, 0f),
                    size = androidx.compose.ui.geometry.Size(w + 1f, 10f),
                )
            }
        }
        Text(
            "${FLIR_MIN_C.toInt()}–${FLIR_MAX_C.toInt()}°C",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

private fun heatOf(unit: Unit3DUi): Float {
    if (unit.status == MinerStatus.OFFLINE) return 0.03f
    val t = unit.chipTempC?.toFloat() ?: return 0.15f
    return ((t - FLIR_MIN_C) / (FLIR_MAX_C - FLIR_MIN_C)).coerceIn(0f, 1f)
}

private fun flirColor(heat: Float, whiteHot: Boolean): Color {
    if (whiteHot) {
        val v = heat.coerceIn(0f, 1f)
        return Color(v, v, v)
    }
    val (r, g, b) = Fleet3D.ironbow(heat)
    return Color(r, g, b)
}

private fun statusColor(status: MinerStatus): Color = when (status) {
    MinerStatus.ONLINE -> HiBrand.accent
    MinerStatus.DEGRADED -> HiBrand.statusDegraded
    MinerStatus.OFFLINE -> Color(0.35f, 0.16f, 0.16f)
    MinerStatus.UNKNOWN -> HiBrand.textSecondary
}
