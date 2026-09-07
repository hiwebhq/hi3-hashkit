package hi3.hashkit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/**
 * Centralized Hi3 branding layer. Colors are snapshot-backed so the whole UI —
 * including non-composable Canvas code that reads these directly — reacts when the
 * theme mode changes, without threading a palette through every call site. The theme
 * root ([Hi3MinerWatchTheme]) sets the active palette.
 */
object HiBrand {
    val appName = "Hi3 Hashkit"

    // Brand ink is constant across themes; the logo blue is the app's primary color.
    val accent = Color(0xFF3987E5)

    var accentAlt by mutableStateOf(Dark.accentAlt)
    var background by mutableStateOf(Dark.background)
    var surface by mutableStateOf(Dark.surface)
    var surfaceRaised by mutableStateOf(Dark.surfaceRaised)
    var outline by mutableStateOf(Dark.outline)
    var textPrimary by mutableStateOf(Dark.textPrimary)
    var textSecondary by mutableStateOf(Dark.textSecondary)
    var statusOnline by mutableStateOf(Dark.statusOnline)
    var statusDegraded by mutableStateOf(Dark.statusDegraded)
    var statusOffline by mutableStateOf(Dark.statusOffline)
    var statusUnknown by mutableStateOf(Dark.statusUnknown)

    fun apply(dark: Boolean) {
        val p: Palette = if (dark) Dark else Light
        accentAlt = p.accentAlt; background = p.background; surface = p.surface
        surfaceRaised = p.surfaceRaised; outline = p.outline
        textPrimary = p.textPrimary; textSecondary = p.textSecondary
        statusOnline = p.statusOnline; statusDegraded = p.statusDegraded
        statusOffline = p.statusOffline; statusUnknown = p.statusUnknown
    }

    interface Palette {
        val accentAlt: Color; val background: Color; val surface: Color
        val surfaceRaised: Color; val outline: Color
        val textPrimary: Color; val textSecondary: Color
        val statusOnline: Color; val statusDegraded: Color
        val statusOffline: Color; val statusUnknown: Color
    }

    /** Dark command-center palette (default, original design). */
    object Dark : Palette {
        override val accentAlt = Color(0xFF6FB1FF)
        override val background = Color(0xFF0B0F14)
        override val surface = Color(0xFF121820)
        override val surfaceRaised = Color(0xFF1A222D)
        override val outline = Color(0xFF2A3542)
        override val textPrimary = Color(0xFFE8EEF4)
        override val textSecondary = Color(0xFF93A3B4)
        override val statusOnline = Color(0xFF2BD97C)
        override val statusDegraded = Color(0xFFF0B429)
        override val statusOffline = Color(0xFFEF5350)
        override val statusUnknown = Color(0xFF78909C)
    }

    /** Light palette: same brand blue, adjusted for contrast on a bright ground. */
    object Light : Palette {
        override val accentAlt = Color(0xFF1C6FD0)
        override val background = Color(0xFFF4F6F9)
        override val surface = Color(0xFFFFFFFF)
        override val surfaceRaised = Color(0xFFEAEEF3)
        override val outline = Color(0xFFCBD5E1)
        override val textPrimary = Color(0xFF0B0F14)
        override val textSecondary = Color(0xFF5A6B7B)
        override val statusOnline = Color(0xFF1B9E57)
        override val statusDegraded = Color(0xFFC98A00)
        override val statusOffline = Color(0xFFD32F2F)
        override val statusUnknown = Color(0xFF78909C)
    }
}

private fun darkScheme() = darkColorScheme(
    primary = HiBrand.accent, onPrimary = Color(0xFFF2F7FF),
    primaryContainer = Color(0xFF1C4A8C), onPrimaryContainer = Color(0xFFD6E5FB),
    secondary = HiBrand.Dark.accentAlt, onSecondary = Color(0xFF071219),
    secondaryContainer = Color(0xFF14355F), onSecondaryContainer = Color(0xFFD6E5FB),
    background = HiBrand.Dark.background, onBackground = HiBrand.Dark.textPrimary,
    surface = HiBrand.Dark.surface, onSurface = HiBrand.Dark.textPrimary,
    surfaceVariant = HiBrand.Dark.surfaceRaised, onSurfaceVariant = HiBrand.Dark.textSecondary,
    outline = HiBrand.Dark.outline, error = HiBrand.Dark.statusOffline,
)

private fun lightScheme() = lightColorScheme(
    primary = HiBrand.accent, onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E5FB), onPrimaryContainer = Color(0xFF0B2E5C),
    secondary = HiBrand.Light.accentAlt, onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E5FB), onSecondaryContainer = Color(0xFF0B2E5C),
    background = HiBrand.Light.background, onBackground = HiBrand.Light.textPrimary,
    surface = HiBrand.Light.surface, onSurface = HiBrand.Light.textPrimary,
    surfaceVariant = HiBrand.Light.surfaceRaised, onSurfaceVariant = HiBrand.Light.textSecondary,
    outline = HiBrand.Light.outline, error = HiBrand.Light.statusOffline,
)

@Composable
fun Hi3MinerWatchTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    androidx.compose.runtime.SideEffect { HiBrand.apply(dark) }
    MaterialTheme(colorScheme = if (dark) darkScheme() else lightScheme(), content = content)
}
