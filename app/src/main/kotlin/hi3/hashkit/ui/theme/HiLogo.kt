package hi3.hashkit.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

/**
 * The in-app Hi3 Hashkit logo lockup: a hashrate-pulse mark (matching the launcher
 * icon) followed by the "Hi3 Hashkit" wordmark — "Hi3" in the text color, "Hashkit"
 * in Hi3 blue. Lives in the branding layer so name, mark, and colors change in one
 * place. Used in the top bar and reusable for onboarding/about.
 */
@Composable
fun HiLogo(
    modifier: Modifier = Modifier,
    markSize: Dp = 26.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
    showMark: Boolean = true,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showMark) {
            HiPulseMark(Modifier.size(markSize))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = wordmark(),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun wordmark(): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = HiBrand.textPrimary)) { append("Hi3 ") }
    withStyle(SpanStyle(color = HiBrand.accent)) { append("Hashkit") }
}

/** The pulse glyph on its own (a hashrate spike), in Hi3 blue. */
@Composable
fun HiPulseMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Polyline normalized to the box (same shape as the launcher mark).
        val pts = listOf(
            0.06f to 0.55f, 0.28f to 0.55f, 0.40f to 0.30f,
            0.52f to 0.80f, 0.64f to 0.42f, 0.72f to 0.55f, 0.94f to 0.55f,
        ).map { (x, y) -> Offset(x * w, y * h) }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path,
            color = HiBrand.accent,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = h * 0.11f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
