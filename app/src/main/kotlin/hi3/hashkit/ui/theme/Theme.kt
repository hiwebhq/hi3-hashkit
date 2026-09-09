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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/**
 * Selectable accent color scheme ("UI Theme"). Only the accent pair changes; the
 * command-center greys and the online/offline status colors stay constant so status
 * always reads the same. BLUE is the original default. Each option carries a dark and a
 * light accent tuned for contrast on the respective ground.
 */
enum class ThemeColor(
    val label: String,
    val darkAccent: Color,
    val darkAccentAlt: Color,
    val lightAccent: Color,
    val lightAccentAlt: Color,
) {
    BLUE("Blue", Color(0xFF3987E5), Color(0xFF6FB1FF), Color(0xFF1C6FD0), Color(0xFF1C6FD0)),
    GREEN("Green", Color(0xFF2BB673), Color(0xFF5FD79B), Color(0xFF158A54), Color(0xFF158A54)),
    ORANGE("Orange", Color(0xFFF08A24), Color(0xFFFFB05A), Color(0xFFD9720F), Color(0xFFD9720F)),
    YELLOW("Yellow", Color(0xFFE6B800), Color(0xFFF3D46A), Color(0xFF9A7B00), Color(0xFF9A7B00)),
    RED("Red", Color(0xFFE5484D), Color(0xFFFF7B7F), Color(0xFFC93338), Color(0xFFC93338)),
    PURPLE("Purple", Color(0xFF8B5CF6), Color(0xFFB794FF), Color(0xFF6D3FD4), Color(0xFF6D3FD4));

    fun accent(dark: Boolean): Color = if (dark) darkAccent else lightAccent
    fun accentAlt(dark: Boolean): Color = if (dark) darkAccentAlt else lightAccentAlt

    companion object {
        fun fromName(name: String?): ThemeColor = entries.firstOrNull { it.name == name } ?: BLUE
    }
}

/**
 * Centralized Hi3 branding layer. Colors are snapshot-backed so the whole UI —
 * including non-composable Canvas code that reads these directly — reacts when the
 * theme mode changes, without threading a palette through every call site. The theme
 * root ([Hi3MinerWatchTheme]) sets the active palette.
 */
object HiBrand {
    val appName = "Hi3 Hashkit"

    // Accent color reacts to the selected UI Theme (default blue). Snapshot-backed so the
    // whole UI — including Canvas code reading it directly — recolors when it changes.
    var accent by mutableStateOf(ThemeColor.BLUE.darkAccent)

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

    fun apply(dark: Boolean, themeColor: ThemeColor = ThemeColor.BLUE) {
        val p: Palette = if (dark) Dark else Light
        accent = themeColor.accent(dark)
        accentAlt = themeColor.accentAlt(dark)
        background = p.background; surface = p.surface
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

/** Text that reads on a filled accent button: dark on bright accents (e.g. yellow), else light. */
private fun onAccent(accent: Color): Color =
    if (accent.luminance() > 0.5f) Color(0xFF071219) else Color(0xFFF2F7FF)

private fun darkScheme(accent: Color, accentAlt: Color) = darkColorScheme(
    primary = accent, onPrimary = onAccent(accent),
    primaryContainer = lerp(accent, Color.Black, 0.55f), onPrimaryContainer = lerp(accent, Color.White, 0.75f),
    secondary = accentAlt, onSecondary = onAccent(accentAlt),
    secondaryContainer = lerp(accent, Color.Black, 0.6f), onSecondaryContainer = lerp(accent, Color.White, 0.75f),
    background = HiBrand.Dark.background, onBackground = HiBrand.Dark.textPrimary,
    surface = HiBrand.Dark.surface, onSurface = HiBrand.Dark.textPrimary,
    surfaceVariant = HiBrand.Dark.surfaceRaised, onSurfaceVariant = HiBrand.Dark.textSecondary,
    outline = HiBrand.Dark.outline, error = HiBrand.Dark.statusOffline,
)

private fun lightScheme(accent: Color, accentAlt: Color) = lightColorScheme(
    primary = accent, onPrimary = onAccent(accent),
    primaryContainer = lerp(accent, Color.White, 0.75f), onPrimaryContainer = lerp(accent, Color.Black, 0.6f),
    secondary = accentAlt, onSecondary = onAccent(accentAlt),
    secondaryContainer = lerp(accent, Color.White, 0.75f), onSecondaryContainer = lerp(accent, Color.Black, 0.6f),
    background = HiBrand.Light.background, onBackground = HiBrand.Light.textPrimary,
    surface = HiBrand.Light.surface, onSurface = HiBrand.Light.textPrimary,
    surfaceVariant = HiBrand.Light.surfaceRaised, onSurfaceVariant = HiBrand.Light.textSecondary,
    outline = HiBrand.Light.outline, error = HiBrand.Light.statusOffline,
)

@Composable
fun Hi3MinerWatchTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeColor: ThemeColor = ThemeColor.BLUE,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    androidx.compose.runtime.SideEffect { HiBrand.apply(dark, themeColor) }
    val accent = themeColor.accent(dark)
    val accentAlt = themeColor.accentAlt(dark)
    val scheme = if (dark) darkScheme(accent, accentAlt) else lightScheme(accent, accentAlt)
    MaterialTheme(colorScheme = scheme, content = content)
}
