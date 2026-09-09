package hi3.hashkit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseValidatorTest {

    @Test fun issuedCodesValidate() {
        val code = LicenseValidator.issue("PRO2026")
        assertTrue(code, LicenseValidator.isValid(code))
    }

    @Test fun validationIgnoresDashesSpacesAndCase() {
        val code = LicenseValidator.issue("ABCD")
        val grouped = code.chunked(4).joinToString("-").lowercase()
        assertTrue(LicenseValidator.isValid(grouped))
    }

    @Test fun rejectsNullBlankAndGarbage() {
        assertFalse(LicenseValidator.isValid(null))
        assertFalse(LicenseValidator.isValid(""))
        assertFalse(LicenseValidator.isValid("not-a-code"))
        assertFalse(LicenseValidator.isValid("HI3")) // too short
    }

    @Test fun rejectsWrongChecksum() {
        val code = LicenseValidator.issue("XYZ")
        // Corrupt the final checksum char.
        val bad = code.dropLast(1) + if (code.last() == 'A') 'B' else 'A'
        assertFalse(LicenseValidator.isValid(bad))
    }

    @Test fun issuedCodeAlwaysStartsWithHi3() {
        assertEquals("HI3", LicenseValidator.issue("anything").take(3))
    }
}
