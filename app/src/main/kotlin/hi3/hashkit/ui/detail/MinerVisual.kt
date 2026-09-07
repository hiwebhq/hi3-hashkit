package hi3.hashkit.ui.detail

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.ui.theme.HiBrand
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stylized 3D-style ("cabinet projection") live render of a miner: the chassis is
 * drawn with a lit top, front and side face; the top carries a chip grid that glows
 * from chip temperature; the front fan spins at a rate derived from the reported fan
 * RPM/percent; a heat aura behind the box tracks temperature. Everything is driven by
 * real telemetry — nothing is invented (a missing value simply doesn't animate).
 *
 * This is a Canvas render, not a photoreal 3D model: no engine dependency, no binary
 * assets. If real glTF models are ever provided, a Filament/SceneView view is the
 * upgrade path.
 */
@Composable
fun MinerVisual(
    status: MinerStatus,
    chipTempC: Double?,
    fanRpm: Int?,
    fanPercent: Int?,
    modifier: Modifier = Modifier,
) {
    val timeMs by produceState(0L) {
        while (true) withInfiniteAnimationFrameMillis { value = it }
    }
    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        drawMiner(timeMs, status, chipTempC, fanRpm, fanPercent)
    }
}

private fun tempColor(t: Double?): Color = when {
    t == null -> HiBrand.textSecondary
    t >= 80 -> HiBrand.statusOffline
    t >= 65 -> HiBrand.statusDegraded
    t >= 45 -> Color(0xFFB6C94A)
    else -> HiBrand.accentAlt
}

private fun DrawScope.drawMiner(
    timeMs: Long,
    status: MinerStatus,
    chipTempC: Double?,
    fanRpm: Int?,
    fanPercent: Int?,
) {
    val t = timeMs / 1000.0
    val cx = size.width / 2f
    val cy = size.height / 2f
    val online = status == MinerStatus.ONLINE || status == MinerStatus.DEGRADED

    // Box dimensions and cabinet-projection depth vector.
    val w = size.width * 0.42f
    val hgt = size.height * 0.42f
    val depth = Offset(w * 0.42f, -hgt * 0.5f)

    val flt = Offset(cx - w / 2f, cy + hgt / 2f)          // front lower-left
    val ftl = Offset(cx - w / 2f, cy - hgt / 2f)          // front upper-left
    val ftr = Offset(cx + w / 2f, cy - hgt / 2f)          // front upper-right
    val fbr = Offset(cx + w / 2f, cy + hgt / 2f)          // front lower-right

    // Heat aura behind the chassis.
    val temp = chipTempC
    val aura = tempColor(temp)
    val pulse = 0.5f + 0.5f * sin(t * 2).toFloat()
    if (online && temp != null) {
        drawCircle(aura.copy(alpha = 0.10f + 0.10f * pulse), radius = w * 1.15f, center = Offset(cx, cy))
    }

    val body = if (online) Color(0xFF1A222D) else Color(0xFF141922)
    val bodyLight = if (online) Color(0xFF243040) else Color(0xFF1A2029)

    // Top face (parallelogram: ftl, ftr, ftr+depth, ftl+depth).
    drawPath(quad(ftl, ftr, ftr + depth, ftl + depth), bodyLight)
    // Right face.
    drawPath(quad(ftr, fbr, fbr + depth, ftr + depth), body.copy(alpha = 0.9f))
    // Front face.
    drawPath(quad(ftl, ftr, fbr, flt), body)
    // Edges.
    listOf(
        ftl to ftr, ftr to fbr, fbr to flt, flt to ftl,
        ftl to (ftl + depth), ftr to (ftr + depth), fbr to (fbr + depth),
        (ftl + depth) to (ftr + depth), (ftr + depth) to (fbr + depth),
    ).forEach { (a, b) -> drawLine(HiBrand.outline, a, b, strokeWidth = 2f) }

    // Chip grid on the top face, glowing by temperature.
    val cols = 4; val rows = 3
    for (r in 0 until rows) for (c in 0 until cols) {
        val u = (c + 0.5f) / cols; val v = (r + 0.5f) / rows
        // bilinear across the top parallelogram
        val top0 = lerp(ftl, ftr, u); val top1 = lerp(ftl + depth, ftr + depth, u)
        val p = lerp(top0, top1, v)
        val glow = if (online) (0.35f + 0.35f * sin(t * 3 + r + c).toFloat()) else 0.12f
        drawCircle(tempColor(temp).copy(alpha = glow.coerceIn(0.12f, 0.8f)), radius = w * 0.035f, center = p)
    }

    // Front fan: spins at a rate derived from reported RPM (scaled to be watchable).
    val fanCenter = Offset(cx, cy + hgt * 0.02f)
    val fanR = hgt * 0.34f
    drawCircle(Color(0xFF0B0F14), radius = fanR * 1.15f, center = fanCenter)
    drawCircle(HiBrand.outline, radius = fanR * 1.15f, center = fanCenter, style = Stroke(2f))
    val rpm = fanRpm ?: (fanPercent?.let { it * 60 })  // fall back to percent if no RPM
    val spinning = online && (rpm ?: 0) > 0
    val bladeColor = if (spinning) HiBrand.accent else HiBrand.textSecondary
    // Visual speed: map rpm (0..8000) to 0..3 rev/s so faster fans clearly spin faster.
    val revPerSec = ((rpm ?: 0) / 8000.0 * 3.0).coerceIn(0.0, 3.0)
    val angle = (t * revPerSec * 360.0).toFloat()
    rotate(degrees = angle, pivot = fanCenter) {
        for (b in 0 until 5) {
            rotate(degrees = b * 72f, pivot = fanCenter) {
                val blade = Path().apply {
                    moveTo(fanCenter.x, fanCenter.y)
                    quadraticBezierTo(
                        fanCenter.x + fanR * 0.5f, fanCenter.y - fanR * 0.2f,
                        fanCenter.x + fanR * 0.15f, fanCenter.y - fanR,
                    )
                    quadraticBezierTo(
                        fanCenter.x - fanR * 0.1f, fanCenter.y - fanR * 0.5f,
                        fanCenter.x, fanCenter.y,
                    )
                    close()
                }
                drawPath(blade, bladeColor.copy(alpha = 0.55f))
            }
        }
    }
    drawCircle(bladeColor, radius = fanR * 0.16f, center = fanCenter)

    // Status LED on the front face.
    val led = when (status) {
        MinerStatus.ONLINE -> HiBrand.statusOnline
        MinerStatus.DEGRADED -> HiBrand.statusDegraded
        MinerStatus.OFFLINE -> HiBrand.statusOffline
        MinerStatus.UNKNOWN -> HiBrand.statusUnknown
    }
    val ledPos = Offset(flt.x + w * 0.12f, flt.y - hgt * 0.14f)
    drawCircle(led.copy(alpha = 0.35f), radius = 9f, center = ledPos)
    drawCircle(led, radius = 5f, center = ledPos)
}

private fun DrawScope.quad(a: Offset, b: Offset, c: Offset, d: Offset): Path =
    Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }

private fun lerp(a: Offset, b: Offset, f: Float) =
    Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
