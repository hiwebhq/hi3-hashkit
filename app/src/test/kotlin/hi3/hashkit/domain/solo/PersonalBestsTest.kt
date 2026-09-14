package hi3.hashkit.domain.solo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalBestsTest {

    @Test
    fun `only a strictly higher share is a new record`() {
        assertTrue(PersonalBests.isNewRecord(4.3e6, null))
        assertTrue(PersonalBests.isNewRecord(4.3e6, 4.2e6))
        assertFalse(PersonalBests.isNewRecord(4.2e6, 4.2e6))
        assertFalse(PersonalBests.isNewRecord(4.1e6, 4.2e6))
        assertFalse(PersonalBests.isNewRecord(null, 1.0))
        assertFalse(PersonalBests.isNewRecord(0.0, null))
    }

    @Test
    fun `percent of a block scales the share against network difficulty`() {
        assertEquals(50.0, PersonalBests.percentOfBlock(5e13, 1e14)!!, 1e-9)
        assertNull(PersonalBests.percentOfBlock(5e13, null))
        assertNull(PersonalBests.percentOfBlock(5e13, 0.0))
    }

    @Test
    fun `formatting keeps tiny fractions readable`() {
        assertEquals("—", PersonalBests.formatPercent(null))
        assertEquals("12.5%", PersonalBests.formatPercent(12.5))
        assertEquals("0.034%", PersonalBests.formatPercent(0.0337))
        assertEquals("0.00340%", PersonalBests.formatPercent(0.0034))
        assertEquals("100%", PersonalBests.formatPercent(150.0))
        assertTrue(PersonalBests.formatPercent(3.4e-9).endsWith("e-09%"))
    }
}
