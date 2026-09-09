package hi3.hashkit.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeColorTest {

    @Test fun fromNameRoundTripsEveryOption() {
        for (c in ThemeColor.entries) {
            assertEquals(c, ThemeColor.fromName(c.name))
        }
    }

    @Test fun fromNameDefaultsToBlueForUnknownOrNull() {
        assertEquals(ThemeColor.BLUE, ThemeColor.fromName(null))
        assertEquals(ThemeColor.BLUE, ThemeColor.fromName(""))
        assertEquals(ThemeColor.BLUE, ThemeColor.fromName("TEAL"))
    }

    @Test fun blueDefaultPreservesOriginalAccents() {
        // The original design's accents must be unchanged so the default looks identical.
        assertEquals(0xFF3987E5.toInt(), ThemeColor.BLUE.darkAccent.toArgb())
        assertEquals(0xFF6FB1FF.toInt(), ThemeColor.BLUE.darkAccentAlt.toArgb())
        assertEquals(0xFF1C6FD0.toInt(), ThemeColor.BLUE.lightAccent.toArgb())
    }

    @Test fun eachOptionHasADistinctDarkAccent() {
        val accents = ThemeColor.entries.map { it.darkAccent.toArgb() }
        assertEquals(accents.size, accents.toSet().size)
        assertNotEquals(ThemeColor.GREEN.darkAccent.toArgb(), ThemeColor.RED.darkAccent.toArgb())
    }
}
