package hi3.hashkit.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.ui.theme.HiBrand

/**
 * Lightweight Canvas line chart of stored hashrate history. Gaps (offline/unavailable
 * samples) are rendered as breaks in the line rather than interpolated — the chart
 * never invents data.
 */
@Composable
fun HashrateChart(
    history: List<MinerTelemetry>,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
    ) {
        val points = history.map { it.timestamp.toEpochMilli() to it.hashrateGhs.value }
        if (points.size < 2) return@Canvas

        val minT = points.first().first
        val maxT = points.last().first
        val spanT = (maxT - minT).coerceAtLeast(1)
        val values = points.mapNotNull { it.second }
        if (values.isEmpty()) return@Canvas
        val maxV = (values.max() * 1.1).coerceAtLeast(1.0)
        val minV = 0.0

        fun x(t: Long) = (t - minT).toFloat() / spanT * size.width
        fun y(v: Double) = size.height - ((v - minV) / (maxV - minV)).toFloat() * size.height

        // Grid lines
        val gridColor = HiBrand.outline.copy(alpha = 0.5f)
        for (i in 1..3) {
            val gy = size.height * i / 4f
            drawLine(
                gridColor,
                Offset(0f, gy),
                Offset(size.width, gy),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }

        // Line with gaps at unavailable samples
        val path = Path()
        var penDown = false
        for ((t, v) in points) {
            if (v == null) {
                penDown = false
                continue
            }
            if (!penDown) {
                path.moveTo(x(t), y(v))
                penDown = true
            } else {
                path.lineTo(x(t), y(v))
            }
        }
        drawPath(
            path,
            color = HiBrand.accent,
            style = Stroke(width = 4f, cap = StrokeCap.Round),
        )
    }
}
