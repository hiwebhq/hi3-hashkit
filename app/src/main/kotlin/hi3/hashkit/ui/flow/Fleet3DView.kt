@file:Suppress("MagicNumber") // a Canvas renderer: the geometry/palette literals ARE the drawing

package hi3.hashkit.ui.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import kotlin.math.atan2
import kotlin.math.hypot

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
private const val TWO_PI = (Math.PI * 2).toFloat()
private const val LABEL_MIN_EDGE_PX = 44f
private const val FAN_MIN_EDGE_PX = 26f
private const val DEFAULT_FAN_RPM = 3000

/** The 3D fleet view: orbit/zoom scene of the fleet with an optional FLIR thermal mode. */
@Suppress("LongMethod") // one declarative screen: controls + camera resolution + canvas
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
    var tour by remember { mutableStateOf(false) }
    var tourStartMs by remember { mutableStateOf(0L) }
    // Frame clock drives fan spin and the drone tour.
    val timeMs by produceState(0L) {
        while (true) withInfiniteAnimationFrameMillis { value = it }
    }

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
    // Drone tour overrides the manual camera; any gesture hands control back.
    val tourF = if (tour) {
        Fleet3D.tourFrame(timeMs - tourStartMs, scene.placed.map { it.center }, pivot)
    } else null
    val focusId = tourF?.focusPlacedIndex?.takeIf { it >= 0 }
        ?.let { scene.placed.getOrNull(it)?.index }
        ?.let { i -> state.units.getOrNull(i)?.id }
    val effectiveSelected = focusId ?: selectedId

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
            tour = tour,
            onTour = { on ->
                tour = on
                if (on) tourStartMs = timeMs
            },
        )
        Box(Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            tour = false
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
                    scene, state.units,
                    tourF?.pivot ?: pivot,
                    tourF?.yaw ?: yaw,
                    tourF?.pitch ?: pitch,
                    tourF?.zoom ?: zoom,
                    flir, whiteHot, hitCenters, effectiveSelected, timeMs,
                )
            }
            state.units.firstOrNull { it.id == effectiveSelected }?.let { SelectedReadout(it, flir) }
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
    tour: Boolean,
    onTour: (Boolean) -> Unit,
) {
    // One compact, scrollable line — the canvas below gets the rest of the screen.
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp),
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
        FilterChip(selected = tour, onClick = { onTour(!tour) }, label = { Text("Tour") })
        // Virtual rack size: pick any 2×2 … 8×8 directly.
        for (n in Fleet3D.RACK_SIZE_MIN..Fleet3D.RACK_SIZE_MAX) {
            FilterChip(
                selected = rackSize == n,
                onClick = { onRackSize(n) },
                label = { Text("$n×$n") },
            )
        }
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
    timeMs: Long,
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
            drawUnitBox(corners, unit, heat, flir, whiteHot, unit.id == selectedId, timeMs)
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
    timeMs: Long,
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
    drawEndFaces(corners, unit, flir, timeMs)
    drawSideLabels(corners, unit, flir)
}

/** A face is toward the viewer when its average depth is nearer than the box center. */
private fun faceForward(corners: List<Fleet3D.Projected>, quad: IntArray): Boolean {
    val centerDepth = corners.map { it.depth }.average()
    return quad.map { corners[it].depth }.average() > centerDepth
}

/**
 * End faces of the machine: FRONT and BACK each carry a spinning intake/exhaust fan
 * (RPM-driven, frozen when the miner is off) with the live telemetry readout painted
 * below it. `bottom`/`top` are the corner pairs of that face's bottom and top edges,
 * used to find the face's "up" direction on screen.
 */
private val END_FACES = listOf(
    Triple(FACES[1], intArrayOf(4, 5), intArrayOf(6, 7)), // front
    Triple(FACES[0], intArrayOf(0, 1), intArrayOf(2, 3)), // back
)

private fun DrawScope.drawEndFaces(
    corners: List<Fleet3D.Projected>,
    unit: Unit3DUi,
    flir: Boolean,
    timeMs: Long,
) {
    for ((quad, bottom, top) in END_FACES) {
        drawEndFace(corners, unit, flir, timeMs, quad, bottom, top)
    }
}

@Suppress("LongParameterList") // face renderer needs the box's full display state
private fun DrawScope.drawEndFace(
    corners: List<Fleet3D.Projected>,
    unit: Unit3DUi,
    flir: Boolean,
    timeMs: Long,
    quad: IntArray,
    bottom: IntArray,
    top: IntArray,
) {
    if (!faceForward(corners, quad)) return
    val fx = quad.map { corners[it].x }.average().toFloat()
    val fy = quad.map { corners[it].y }.average().toFloat()
    val edge = hypot(
        corners[quad[1]].x - corners[quad[0]].x,
        corners[quad[1]].y - corners[quad[0]].y,
    )
    if (edge < FAN_MIN_EDGE_PX) return
    // Face-local "up" on screen: bottom-edge midpoint -> top-edge midpoint.
    val upX = (corners[top[0]].x + corners[top[1]].x) / 2f - (corners[bottom[0]].x + corners[bottom[1]].x) / 2f
    val upY = (corners[top[0]].y + corners[top[1]].y) / 2f - (corners[bottom[0]].y + corners[bottom[1]].y) / 2f
    // Fan sits in the upper part of the face, leaving the lower part for telemetry.
    val center = Offset(fx + upX * 0.17f, fy + upY * 0.17f)
    val r = edge * 0.26f
    // Recessed housing + rim.
    drawCircle(Color.Black.copy(alpha = if (flir) 0.5f else 0.38f), r, center)
    drawCircle(
        if (flir) Color(0.35f, 0.35f, 0.35f) else Color(0.75f, 0.78f, 0.82f),
        r,
        center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = (r * 0.09f).coerceAtLeast(1.5f)),
    )
    val rpm = when {
        unit.status == MinerStatus.OFFLINE -> 0
        else -> unit.fanRpm ?: DEFAULT_FAN_RPM
    }
    val angle = if (rpm <= 0) 0.6f else (timeMs / 1000f * rpm / 60f * TWO_PI) % TWO_PI
    val bladeColor = when {
        flir -> Color(0.45f, 0.45f, 0.45f)
        rpm <= 0 -> Color(0.45f, 0.45f, 0.48f)
        else -> Color(0.82f, 0.85f, 0.9f)
    }
    repeat(3) { k ->
        val a = angle + k * (TWO_PI / 3)
        drawLine(
            bladeColor,
            Offset(center.x + r * 0.18f * kotlin.math.cos(a), center.y + r * 0.18f * kotlin.math.sin(a)),
            Offset(center.x + r * 0.85f * kotlin.math.cos(a), center.y + r * 0.85f * kotlin.math.sin(a)),
            strokeWidth = (r * 0.24f).coerceAtLeast(2f),
        )
    }
    drawCircle(bladeColor, (r * 0.16f).coerceAtLeast(1.5f), center)
    drawEndTelemetry(corners, unit, flir, bottom, edge, fx, fy)
}

/** Hashrate + chip temp painted under the fan, aligned to the face's bottom edge. */
@Suppress("LongParameterList") // positioned inside an already-projected face
private fun DrawScope.drawEndTelemetry(
    corners: List<Fleet3D.Projected>,
    unit: Unit3DUi,
    flir: Boolean,
    bottom: IntArray,
    edge: Float,
    fx: Float,
    fy: Float,
) {
    if (edge < LABEL_MIN_EDGE_PX) return
    val a = corners[bottom[0]]
    val b = corners[bottom[1]]
    var deg = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
    if (deg > 90f) deg -= 180f
    if (deg < -90f) deg += 180f
    val paint = android.graphics.Paint().apply {
        color = if (flir) android.graphics.Color.WHITE
        else android.graphics.Color.argb(235, 255, 255, 255)
        textSize = (edge / 6.4f).coerceIn(8f, 20f)
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    val lines = listOf(
        Units.formatHashrate(unit.hashrateGhs),
        unit.chipTempC?.let { "%.0f°C".format(it) } ?: "—",
    )
    val lh = paint.textSize * 1.12f
    val native = drawContext.canvas.nativeCanvas
    native.save()
    native.translate(fx, fy)
    native.rotate(deg)
    // Local +y is "down" the face after rotation: start below the fan, near the bottom edge.
    lines.forEachIndexed { i, line ->
        native.drawText(line, 0f, edge * 0.14f + i * lh + paint.textSize * 0.35f, paint)
    }
    native.restore()
}

/** Identity (name / IP) on the flanks; live telemetry lives under the end-face fans. */
private fun DrawScope.drawSideLabels(
    corners: List<Fleet3D.Projected>,
    unit: Unit3DUi,
    flir: Boolean,
) {
    for (f in intArrayOf(4, 5)) { // left, right
        drawOneSideLabel(corners, unit, flir, FACES[f])
    }
}

private fun DrawScope.drawOneSideLabel(
    corners: List<Fleet3D.Projected>,
    unit: Unit3DUi,
    flir: Boolean,
    quad: IntArray,
) {
    if (!faceForward(corners, quad)) return
    run {
        val a = corners[quad[0]]
        val b = corners[quad[3]] // the along-depth edge: text baseline direction
        val len = hypot(b.x - a.x, b.y - a.y)
        if (len < LABEL_MIN_EDGE_PX) return
        val fx = quad.map { corners[it].x }.average().toFloat()
        val fy = quad.map { corners[it].y }.average().toFloat()
        var deg = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
        if (deg > 90f) deg -= 180f
        if (deg < -90f) deg += 180f
        val paint = android.graphics.Paint().apply {
            color = if (flir) android.graphics.Color.WHITE
            else android.graphics.Color.argb(235, 255, 255, 255)
            textSize = (len / 7.2f).coerceIn(9f, 24f)
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val lines = listOf(
            unit.name.take(14),
            unit.host,
        )
        val lh = paint.textSize * 1.12f
        val native = drawContext.canvas.nativeCanvas
        native.save()
        native.translate(fx, fy)
        native.rotate(deg)
        lines.forEachIndexed { i, line ->
            native.drawText(line, 0f, (i - (lines.size - 1) / 2f) * lh + paint.textSize * 0.35f, paint)
        }
        native.restore()
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
