package hi3.hashkit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Centralized Hi3 branding layer. Colors, name, and (eventually) logo assets are all
 * defined here so official Hi3 branding can be swapped in without touching screens.
 */
object HiBrand {
    val appName = "Hi3 Hashkit"

    // Dark command-center palette (original design, not derived from any other product).
    val background = Color(0xFF0B0F14)
    val surface = Color(0xFF121820)
    val surfaceRaised = Color(0xFF1A222D)
    val outline = Color(0xFF2A3542)

    // Hi3 logo ink (dark variant, from mmp.hi3.cc --logo-ink) — the app's primary color.
    val accent = Color(0xFF3987E5)
    val accentAlt = Color(0xFF6FB1FF)   // lighter blue for secondary emphasis

    val textPrimary = Color(0xFFE8EEF4)
    val textSecondary = Color(0xFF93A3B4)

    // Status language
    val statusOnline = Color(0xFF2BD97C)
    val statusDegraded = Color(0xFFF0B429)
    val statusOffline = Color(0xFFEF5350)
    val statusUnknown = Color(0xFF78909C)
}

private val DarkScheme = darkColorScheme(
    primary = HiBrand.accent,
    onPrimary = Color(0xFFF2F7FF),
    primaryContainer = Color(0xFF1C4A8C),
    onPrimaryContainer = Color(0xFFD6E5FB),
    secondary = HiBrand.accentAlt,
    onSecondary = Color(0xFF071219),
    secondaryContainer = Color(0xFF14355F),
    onSecondaryContainer = Color(0xFFD6E5FB),
    background = HiBrand.background,
    onBackground = HiBrand.textPrimary,
    surface = HiBrand.surface,
    onSurface = HiBrand.textPrimary,
    surfaceVariant = HiBrand.surfaceRaised,
    onSurfaceVariant = HiBrand.textSecondary,
    outline = HiBrand.outline,
    error = HiBrand.statusOffline,
)

@Composable
fun Hi3MinerWatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // v1 commits to the dark command-center look in both system themes.
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content,
    )
}
