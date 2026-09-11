package hi3.hashkit.ui.flow

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.core.Units
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.ui.theme.HiBrand
import kotlin.math.hypot
import kotlin.math.sin

/** Miner node screen positions — shared by the renderer and tap hit-testing. */
private fun minerLayout(w: Float, h: Float, count: Int): List<Offset> =
    (0 until count).map { i ->
        val x = if (count <= 1) w / 2f else w * (0.08f + 0.84f * i / (count - 1).coerceAtLeast(1))
        Offset(x, h * 0.78f)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowScreen(
    onBack: () -> Unit,
    onMinerClick: (Long) -> Unit,
    viewModel: FlowViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.startProbing() }
    val timeMs by produceState(0L) {
        while (true) withInfiniteAnimationFrameMillis { value = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flow", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { LiveIndicator(liveStatusOf(state)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        containerColor = HiBrand.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SummaryCards(state)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
                    .pointerInput(state.miners.size) {
                        detectTapGestures { pos ->
                            val layout = minerLayout(size.width.toFloat(), size.height.toFloat(), state.miners.size)
                            val idx = layout.indexOfFirst { (it - pos).getDistance() < 56f }
                            if (idx >= 0) onMinerClick(state.miners[idx].id)
                        }
                    },
            ) {
                drawPipeline(state, timeMs)
            }
        }
    }
}

private enum class LiveStatus(val label: String, val color: Color, val pulse: Boolean) {
    LIVE("LIVE", HiBrand.statusOnline, true),
    STALE("STALE", HiBrand.statusDegraded, false),
    OFFLINE("OFFLINE", HiBrand.statusOffline, false),
}

/** Honest liveness for the header badge: red if the uplink is down, amber if no miner is
 *  currently reachable, otherwise green. */
private fun liveStatusOf(state: FlowUiState): LiveStatus = when {
    !state.internetUp -> LiveStatus.OFFLINE
    state.minerCount > 0 && state.onlineCount == 0 -> LiveStatus.STALE
    else -> LiveStatus.LIVE
}

@Composable
private fun LiveIndicator(status: LiveStatus) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 16.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .alpha(if (status.pulse) alpha else 1f)
                .background(status.color, androidx.compose.foundation.shape.CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            status.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = status.color,
        )
    }
}

@Composable
private fun SummaryCards(state: FlowUiState) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { StatCard("HASHRATE", Units.formatHashrate(state.totalHashrateGhs), HiBrand.accent) }
        item {
            StatCard(
                "POWER",
                Units.formatPower(state.totalPowerW) + if (state.anyEstimatedPower) " *" else "",
                HiBrand.textPrimary,
            )
        }
        item {
            StatCard(
                "UPLINK",
                if (state.internetUp) "Online" else "DOWN",
                if (state.internetUp) HiBrand.statusOnline else HiBrand.statusOffline,
            )
        }
        item {
            val worst = state.stratums.minByOrNull { it.latencyMs ?: Long.MAX_VALUE }
            StatCard(
                "POOL PING",
                worst?.latencyMs?.let { "$it ms" } ?: "—",
                if (state.stratums.any { !it.reachable }) HiBrand.statusOffline else HiBrand.accentAlt,
            )
        }
        item {
            StatCard(
                "MINERS",
                "${state.onlineCount}/${state.minerCount}",
                if (state.onlineCount == state.minerCount) HiBrand.statusOnline else HiBrand.statusDegraded,
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, valueColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = HiBrand.textSecondary)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor, fontWeight = FontWeight.Bold)
        }
    }
}

private fun statusColor(s: MinerStatus, health: Float): Color = when (s) {
    MinerStatus.OFFLINE -> HiBrand.statusOffline
    MinerStatus.UNKNOWN -> HiBrand.statusUnknown
    else -> when {
        health >= 0.9f -> HiBrand.statusOnline
        health >= 0.75f -> HiBrand.statusDegraded
        else -> HiBrand.statusOffline
    }
}

/**
 * Draws Network (top) → Stratum (middle) → Miners (bottom) with share particles
 * streaming upward. Particle density/speed scales with each miner's hashrate, so a
 * busy miner visibly streams more shares; offline miners show a dim static link.
 */
private fun DrawScope.drawPipeline(state: FlowUiState, timeMs: Long) {
    val w = size.width
    val h = size.height
    val t = timeMs / 1000.0

    val networkPos = Offset(w / 2f, h * 0.13f)
    val stratums = state.stratums
    val stratumY = h * 0.44f
    val stratumPos = stratums.mapIndexed { i, _ ->
        val x = if (stratums.size == 1) w / 2f else w * (0.18f + 0.64f * i / (stratums.size - 1).coerceAtLeast(1))
        Offset(x, stratumY)
    }
    val stratumIndexByKey = stratums.mapIndexed { i, s -> "${s.host}:${s.port}" to i }.toMap()

    val miners = state.miners
    val minerPos = minerLayout(w, h, miners.size)

    // Edges: stratum -> network (uplink), colored by reachability/internet.
    stratums.forEachIndexed { i, s ->
        val up = state.internetUp && s.reachable
        val col = when {
            !up -> HiBrand.statusOffline
            s.anyFallback -> HiBrand.statusDegraded
            else -> HiBrand.accentAlt
        }
        drawLink(stratumPos[i], networkPos, col)
        if (up) drawParticles(stratumPos[i], networkPos, col, t, density = 3, speed = 0.4)
    }

    // Edges: miner -> its stratum, colored by miner health; particles ∝ hashrate.
    miners.forEachIndexed { i, m ->
        val sIdx = stratumIndexByKey[m.stratumKey] ?: 0
        if (sIdx >= stratumPos.size) return@forEachIndexed
        val col = statusColor(m.status, m.healthFraction)
        drawLink(minerPos[i], stratumPos[sIdx], col.copy(alpha = 0.5f))
        if (m.status != MinerStatus.OFFLINE && (m.hashrateGhs ?: 0.0) > 0) {
            val ths = (m.hashrateGhs ?: 0.0) / 1000.0
            val density = (1 + ths.toInt()).coerceIn(1, 6)
            drawParticles(minerPos[i], stratumPos[sIdx], col, t, density, speed = 0.5 + ths * 0.05)
        }
    }

    // Nodes. The Bitcoin network is a chain of blocks scrolling left→right.
    drawBlockchain(networkPos, w, t, HiBrand.accent)
    stratums.forEachIndexed { i, s ->
        val col = when {
            !(state.internetUp && s.reachable) -> HiBrand.statusOffline
            s.anyFallback -> HiBrand.statusDegraded
            else -> HiBrand.accentAlt
        }
        drawNode(stratumPos[i], 22f, col, pulse = t + i)
    }
    // Adjacent-slot spacing; the ASIC glyph is ~2× bigger now, still capped to the slot so
    // dense fleets don't overlap.
    val adjSpacing = if (miners.size > 1) w * 0.84f / (miners.size - 1) else w * 0.6f
    val boxW = (adjSpacing * 0.82f).coerceAtMost(110f)
    miners.forEachIndexed { i, m ->
        drawAsicMiner(
            minerPos[i], boxW,
            statusColor(m.status, m.healthFraction),
            pulse = t + i * 0.3,
            spinning = m.status != MinerStatus.OFFLINE && (m.hashrateGhs ?: 0.0) > 0,
        )
    }

    // Labels via native canvas.
    drawContext.canvas.nativeCanvas.apply {
        val label = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#93A3B4")
            textSize = 48f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val strong = android.graphics.Paint(label).apply {
            color = android.graphics.Color.parseColor("#E8EEF4")
            textSize = 50f
        }
        // Bitcoin network node: big current block number, with a small caption below.
        val bignum = android.graphics.Paint(strong).apply { textSize = 64f }
        val height = state.blockHeight
        drawText(
            if (height != null) "BLOCK ${"%,d".format(height)}" else "BITCOIN NETWORK",
            networkPos.x, networkPos.y - 66f, bignum,
        )
        val caption = if (height != null) "BITCOIN NETWORK" else "waiting for block height…"
        drawText(
            caption + (state.networkDifficulty?.let { "   ·   diff ${Units.formatDifficulty(it)}" } ?: ""),
            networkPos.x, networkPos.y + 80f, label,
        )
        stratums.forEachIndexed { i, s ->
            drawText(s.label, stratumPos[i].x, stratumPos[i].y - 52f, label)
            val lat = s.latencyMs?.let { "${it}ms" } ?: "unreachable"
            drawText(
                (if (s.anyFallback) "fallback · " else "") + lat,
                stratumPos[i].x, stratumPos[i].y + 68f, label,
            )
        }
        // Miner labels: staggered across two rows + ellipsized to the slot so adjacent labels
        // never overlap, however many miners there are. Spacing scales with the larger text.
        val minerLabel = android.graphics.Paint(label).apply { textSize = 44f }
        val minerHash = android.graphics.Paint(strong).apply { textSize = 44f }
        val adjSpacing = if (miners.size > 1) w * 0.84f / (miners.size - 1) else w * 0.6f
        val boxW = (adjSpacing * 0.82f).coerceAtMost(110f)
        val boxH = boxW * 0.6f
        // Staggered rows double the effective horizontal room for a given row.
        val maxLabelW = (adjSpacing * 1.85f - 8f).coerceAtLeast(40f)
        val lineH = 46f
        miners.forEachIndexed { i, m ->
            val stagger = (i % 2) * lineH
            val name = fitText(minerLabel, m.name, maxLabelW)
            val hash = fitText(minerHash, Units.formatHashrate(m.hashrateGhs), maxLabelW)
            drawText(hash, minerPos[i].x, minerPos[i].y - boxH / 2f - 26f - stagger, minerHash)
            drawText(name, minerPos[i].x, minerPos[i].y + boxH / 2f + 46f + stagger, minerLabel)
        }
    }
}

/** Truncate [text] with an ellipsis until it fits within [maxWidth] px for [paint]. */
private fun fitText(paint: android.graphics.Paint, text: String, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
    return text.substring(0, end).trimEnd() + "…"
}

private fun DrawScope.drawLink(from: Offset, to: Offset, color: Color) {
    drawLine(color.copy(alpha = 0.35f), from, to, strokeWidth = 3f)
}

private fun DrawScope.drawParticles(
    from: Offset,
    to: Offset,
    color: Color,
    t: Double,
    density: Int,
    speed: Double,
) {
    val len = hypot((to.x - from.x).toDouble(), (to.y - from.y).toDouble())
    if (len < 1) return
    for (i in 0 until density) {
        val frac = (((t * speed) + i.toDouble() / density) % 1.0).toFloat()
        val p = Offset(from.x + (to.x - from.x) * frac, from.y + (to.y - from.y) * frac)
        // Fade in/out at the ends.
        val alpha = (sin(frac * Math.PI).toFloat()).coerceIn(0.15f, 1f)
        drawCircle(color.copy(alpha = alpha), radius = 4f, center = p)
    }
}

/**
 * The Bitcoin network drawn as a chain of blocks scrolling left→right across the top. Blocks
 * are brightest near screen-center (the current tip the uplinks connect to) and fade toward the
 * edges. The tip block number is drawn as a big label by the caller.
 */
private fun DrawScope.drawBlockchain(center: Offset, w: Float, t: Double, color: Color) {
    val blockW = 60f
    val blockH = 44f
    val gap = 28f
    val spacing = blockW + gap
    val scroll = ((t * 34.0) % spacing).toFloat() // px/sec, moving right
    val y = center.y
    val corner = androidx.compose.ui.geometry.CornerRadius(9f)
    val cols = (w / spacing).toInt() + 4
    for (k in -2..cols) {
        val cx = k * spacing + scroll - spacing
        if (cx + blockW < 0f || cx - blockW > w) continue
        // Brightest at the tip (screen center), fading toward the edges.
        val bright = (1f - kotlin.math.abs(cx - center.x) / (w * 0.5f)).coerceIn(0.12f, 1f)
        // Chain link to the next block.
        drawLine(
            color.copy(alpha = 0.22f * bright),
            Offset(cx + blockW / 2f, y), Offset(cx + blockW / 2f + gap, y),
            strokeWidth = 3f,
        )
        val tl = Offset(cx - blockW / 2f, y - blockH / 2f)
        val sz = androidx.compose.ui.geometry.Size(blockW, blockH)
        drawRoundRect(color.copy(alpha = 0.05f + 0.14f * bright), topLeft = tl, size = sz, cornerRadius = corner)
        drawRoundRect(color.copy(alpha = 0.35f + 0.5f * bright), topLeft = tl, size = sz, cornerRadius = corner, style = Stroke(width = 2.5f))
        // Inner divider so it reads as a block.
        drawLine(color.copy(alpha = 0.28f * bright), Offset(cx - blockW / 2f + 9f, y), Offset(cx + blockW / 2f - 9f, y), strokeWidth = 1.5f)
    }
    // Soft glow at the tip.
    val pulse = (0.6f + 0.4f * sin(t * 2).toFloat())
    drawCircle(color.copy(alpha = 0.12f * pulse), radius = blockH * 1.6f, center = center)
}

private fun DrawScope.drawNode(center: Offset, radius: Float, color: Color, pulse: Double) {
    val glow = (0.6f + 0.4f * sin(pulse * 2).toFloat())
    drawCircle(color.copy(alpha = 0.18f * glow), radius = radius * 2.1f, center = center)
    drawCircle(color.copy(alpha = 0.30f), radius = radius * 1.4f, center = center)
    drawCircle(color, radius = radius, center = center)
    drawCircle(HiBrand.background, radius = radius, center = center, style = Stroke(width = 3f))
}

/**
 * A little ASIC-miner glyph: a chassis box with two spinning intake fans, tinted by the
 * miner's status color. Online miners' fans spin (via [pulse]); offline miners are static.
 */
private fun DrawScope.drawAsicMiner(
    center: Offset,
    boxW: Float,
    color: Color,
    pulse: Double,
    spinning: Boolean,
) {
    val boxH = boxW * 0.6f
    val topLeft = Offset(center.x - boxW / 2f, center.y - boxH / 2f)
    val corner = androidx.compose.ui.geometry.CornerRadius(boxW * 0.10f)
    val glowA = (0.10f + 0.10f * sin(pulse * 2).toFloat()).coerceIn(0.05f, 0.22f)
    // Soft status glow behind the chassis.
    drawRoundRect(
        color = color.copy(alpha = glowA),
        topLeft = Offset(topLeft.x - 6f, topLeft.y - 6f),
        size = androidx.compose.ui.geometry.Size(boxW + 12f, boxH + 12f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(boxW * 0.16f),
    )
    // Chassis: dark raised body with a status-colored edge.
    drawRoundRect(
        color = HiBrand.surfaceRaised,
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(boxW, boxH),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(boxW, boxH),
        cornerRadius = corner,
        style = Stroke(width = 2.5f),
    )
    // Two intake fans on the face.
    val fanR = boxH * 0.34f
    val fanY = center.y
    val offsetsX = listOf(center.x - boxW * 0.24f, center.x + boxW * 0.24f)
    offsetsX.forEachIndexed { fi, fx ->
        val fanCenter = Offset(fx, fanY)
        drawCircle(color.copy(alpha = 0.20f), radius = fanR, center = fanCenter)
        drawCircle(color, radius = fanR, center = fanCenter, style = Stroke(width = 2f))
        // Blades: 3 spokes, rotating (opposite directions per fan) when spinning.
        val dir = if (fi == 0) 1.0 else -1.0
        val base = if (spinning) pulse * 3.0 * dir else 0.4 * dir
        for (b in 0 until 3) {
            val a = base + b * (2 * Math.PI / 3)
            val end = Offset(
                (fx + fanR * 0.82f * kotlin.math.cos(a)).toFloat(),
                (fanY + fanR * 0.82f * kotlin.math.sin(a)).toFloat(),
            )
            drawLine(color, fanCenter, end, strokeWidth = 2f)
        }
        drawCircle(color, radius = fanR * 0.16f, center = fanCenter) // hub
    }
}
