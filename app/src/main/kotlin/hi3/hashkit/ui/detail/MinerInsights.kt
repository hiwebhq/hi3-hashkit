package hi3.hashkit.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hi3.hashkit.core.Units
import hi3.hashkit.domain.model.ChainReading
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.ui.theme.HiBrand

/** Efficiency (J/TH) value for a sample: use the reported/calculated field, else derive it. */
private fun MinerTelemetry.efficiency(): Double? =
    efficiencyJTh.value ?: Units.efficiencyJTh(powerW.value, hashrateGhs.value)

/**
 * Line chart of stored efficiency (J/TH) history. Lower is better, so the y-axis is
 * auto-ranged around the data rather than pinned to zero. Gaps (no power/hashrate) break
 * the line rather than interpolating.
 */
@Composable
fun EfficiencyChart(
    history: List<MinerTelemetry>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val points = history.map { it.timestamp.toEpochMilli() to it.efficiency() }
        val values = points.mapNotNull { it.second }.filter { it > 0 }
        if (points.size < 2 || values.size < 2) return@Canvas

        val minT = points.first().first
        val spanT = (points.last().first - minT).coerceAtLeast(1)
        val lo = values.min()
        val hi = values.max()
        val pad = ((hi - lo) * 0.15).coerceAtLeast(0.5)
        val minV = (lo - pad).coerceAtLeast(0.0)
        val maxV = (hi + pad).coerceAtLeast(minV + 1.0)

        fun x(t: Long) = (t - minT).toFloat() / spanT * size.width
        fun y(v: Double) = size.height - ((v - minV) / (maxV - minV)).toFloat() * size.height

        val gridColor = HiBrand.outline.copy(alpha = 0.5f)
        for (i in 1..3) {
            val gy = size.height * i / 4f
            drawLine(
                gridColor, Offset(0f, gy), Offset(size.width, gy),
                strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }

        val path = Path()
        var penDown = false
        for ((t, v) in points) {
            if (v == null || v <= 0) { penDown = false; continue }
            if (!penDown) { path.moveTo(x(t), y(v)); penDown = true } else path.lineTo(x(t), y(v))
        }
        drawPath(path, color = HiBrand.accentAlt, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

/** Min / current / max J/TH over the window, for the caption under the chart. */
fun efficiencyStats(history: List<MinerTelemetry>): Triple<Double, Double, Double>? {
    val values = history.mapNotNull { it.efficiency() }.filter { it > 0 }
    if (values.isEmpty()) return null
    val current = history.lastOrNull { (it.efficiency() ?: 0.0) > 0 }?.efficiency() ?: values.last()
    return Triple(values.min(), current, values.max())
}

/**
 * Per-chain / per-board health table. Shown only for miners whose firmware reports it
 * (Antminer-class). Flags a chain with a dead chip (red count) or a hashrate far below
 * the miner's per-chain average.
 */
@Composable
fun PerChipHealthCard(perChain: List<ChainReading>, fahrenheit: Boolean) {
    if (perChain.isEmpty()) return
    val rates = perChain.mapNotNull { it.hashrateGhs }.filter { it > 0 }
    val avg = if (rates.isEmpty()) 0.0 else rates.average()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Header row
        Row(Modifier.fillMaxWidth()) {
            HeaderCell("Board", 1.1f)
            HeaderCell("Hashrate", 1.4f)
            HeaderCell("Chips", 1.1f)
            HeaderCell("HW err", 0.9f)
            HeaderCell("Temp", 0.9f)
        }
        perChain.forEach { c ->
            val weak = avg > 0 && (c.hashrateGhs ?: 0.0) in 0.0..(avg * 0.85)
            val dead = (c.chipsDead ?: 0) > 0
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (dead) HiBrand.statusOffline.copy(alpha = 0.12f)
                        else if (weak) HiBrand.statusDegraded.copy(alpha = 0.12f)
                        else HiBrand.surfaceRaised.copy(alpha = 0.4f)
                    )
                    .padding(vertical = 6.dp, horizontal = 6.dp),
            ) {
                Cell("#${c.index}", 1.1f, HiBrand.textPrimary)
                Cell(
                    c.hashrateGhs?.let { Units.formatHashrate(it) } ?: "—",
                    1.4f,
                    if (weak) HiBrand.statusDegraded else HiBrand.textPrimary,
                )
                val chips = when {
                    c.chipsTotal != null && c.chipsDead != null -> "${c.chipsTotal - c.chipsDead}/${c.chipsTotal}"
                    c.chipsActive != null -> "${c.chipsActive}"
                    else -> "—"
                }
                Cell(chips, 1.1f, if (dead) HiBrand.statusOffline else HiBrand.textPrimary)
                Cell(c.hwErrors?.toString() ?: "—", 0.9f, HiBrand.textSecondary)
                Cell(
                    c.tempC?.let { Units.formatTemp(it, fahrenheit) } ?: "—",
                    0.9f,
                    HiBrand.textSecondary,
                )
            }
        }
        val deadTotal = perChain.sumOf { it.chipsDead ?: 0 }
        Text(
            if (deadTotal > 0)
                "⚠ $deadTotal failed chip(s) detected across ${perChain.size} board(s)."
            else
                "All ${perChain.size} board(s) reporting healthy.",
            style = MaterialTheme.typography.labelSmall,
            color = if (deadTotal > 0) HiBrand.statusOffline else HiBrand.textSecondary,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        color = HiBrand.textSecondary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
    text: String,
    weight: Float,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}
