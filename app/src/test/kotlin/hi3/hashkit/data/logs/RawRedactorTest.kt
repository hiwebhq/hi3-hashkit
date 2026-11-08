package hi3.hashkit.data.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawRedactorTest {

    @Test
    fun `credential-like string fields are masked before storage`() {
        val raw = """{"hashRate":512.3,"ssid":"HomeNet","wifiPass":"hunter2","stratumUser":"bc1qabc.worker",""" +
            """"stratumPassword":"x","apiToken":"t0k3n","psk":"pre-shared","temp":61}"""
        val out = RawRedactor.redact(raw)!!
        listOf("HomeNet", "hunter2", "bc1qabc", "t0k3n", "pre-shared").forEach { assertFalse(it, it in out) }
        // Numeric telemetry and field names survive so the viewer still shows structure.
        assertTrue(out.contains("\"hashRate\":512.3"))
        assertTrue(out.contains("\"temp\":61"))
        assertTrue(out.contains("\"ssid\": \"[redacted]\""))
        assertTrue(out.contains("\"stratumPassword\": \"[redacted]\""))
    }

    @Test
    fun `null passes through and plain bodies are unchanged`() {
        assertNull(RawRedactor.redact(null))
        val plain = """{"power":14.2,"fanrpm":4200,"version":"v2.15.1"}"""
        assertEquals(plain, RawRedactor.redact(plain))
    }
}
