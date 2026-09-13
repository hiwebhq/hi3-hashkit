package hi3.hashkit.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Shared chip-temperature ramp (MinerVisual, site heatmap). Bands follow the health-score
 * thresholds: cool < 45, warm < 65, hot < 80 (degraded), critical >= 80. Reads HiBrand's
 * snapshot-backed colors so Dark/Light both work; deliberately NOT derived from the accent
 * pair — temperature must read the same under every accent theme.
 */
object TempColors {

    const val WARM_C = 45.0
    const val HOT_C = 65.0
    const val CRITICAL_C = 80.0

    /** The one non-brand color: the warm middle band, legible on dark and light surfaces. */
    @Suppress("MagicNumber") // a hand-tuned color literal, same value MinerVisual always used
    val warm = Color(0xFFB6C94A)

    fun tempColor(t: Double?): Color = when {
        t == null -> HiBrand.textSecondary
        t >= CRITICAL_C -> HiBrand.statusOffline
        t >= HOT_C -> HiBrand.statusDegraded
        t >= WARM_C -> warm
        else -> HiBrand.accentAlt
    }
}
