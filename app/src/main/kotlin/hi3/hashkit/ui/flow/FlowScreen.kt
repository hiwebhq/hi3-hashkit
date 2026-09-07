package hi3.hashkit.ui.flow

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
        Offset(x, h * 0.80f)
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
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, fontWeight = FontWeight.Bold)
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

    val networkPos = Offset(w / 2f, h * 0.10f)
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

    // Nodes.
    drawNode(networkPos, 30f, HiBrand.accent, pulse = t)
    stratums.forEachIndexed { i, s ->
        val col = when {
            !(state.internetUp && s.reachable) -> HiBrand.statusOffline
            s.anyFallback -> HiBrand.statusDegraded
            else -> HiBrand.accentAlt
        }
        drawNode(stratumPos[i], 22f, col, pulse = t + i)
    }
    miners.forEachIndexed { i, m ->
        drawNode(minerPos[i], 14f, statusColor(m.status, m.healthFraction), pulse = t + i * 0.3)
    }

    // Labels via native canvas.
    drawContext.canvas.nativeCanvas.apply {
        val label = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#93A3B4")
            textSize = 26f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val strong = android.graphics.Paint(label).apply {
            color = android.graphics.Color.parseColor("#E8EEF4")
            textSize = 28f
        }
        drawText("BITCOIN NETWORK", networkPos.x, networkPos.y - 44f, strong)
        state.networkDifficulty?.let {
            drawText("diff ${Units.formatDifficulty(it)}", networkPos.x, networkPos.y + 52f, label)
        }
        stratums.forEachIndexed { i, s ->
            drawText(s.label, stratumPos[i].x, stratumPos[i].y - 34f, label)
            val lat = s.latencyMs?.let { "${it}ms" } ?: "unreachable"
            drawText(
                (if (s.anyFallback) "fallback · " else "") + lat,
                stratumPos[i].x, stratumPos[i].y + 44f, label,
            )
        }
        miners.forEachIndexed { i, m ->
            val short = m.name.take(10)
            drawText(short, minerPos[i].x, minerPos[i].y + 34f, label)
            drawText(Units.formatHashrate(m.hashrateGhs), minerPos[i].x, minerPos[i].y - 24f, label)
        }
    }
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

private fun DrawScope.drawNode(center: Offset, radius: Float, color: Color, pulse: Double) {
    val glow = (0.6f + 0.4f * sin(pulse * 2).toFloat())
    drawCircle(color.copy(alpha = 0.18f * glow), radius = radius * 2.1f, center = center)
    drawCircle(color.copy(alpha = 0.30f), radius = radius * 1.4f, center = center)
    drawCircle(color, radius = radius, center = center)
    drawCircle(HiBrand.background, radius = radius, center = center, style = Stroke(width = 3f))
}
