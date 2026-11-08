package hi3.hashkit.integrations.poolspeed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StratumTargetTest {

    @Test
    fun `only stratum-plausible ports may be probed`() {
        listOf(3333, 4444, 25, 443, 1024, 65535).filter { it != 25 }
            .forEach { assertTrue("$it", StratumTarget.isAllowedPort(it)) }
        // A compromised miner must not turn the phone into a scanner of SSH, SMTP, printers, routers...
        listOf(22, 23, 25, 80, 139, 445, 631, 0, -1, 65536).forEach { assertFalse("$it", StratumTarget.isAllowedPort(it)) }
    }

    @Test
    fun `line reads are capped`() {
        val ok = "{\"id\":1,\"result\":true}\n".toByteArray().inputStream()
        assertEquals("{\"id\":1,\"result\":true}", StratumTarget.readLineCapped(ok))
        val endless = ByteArray(StratumTarget.MAX_LINE_BYTES + 10) { 'a'.code.toByte() }.inputStream()
        assertNull(StratumTarget.readLineCapped(endless))
        assertNull(StratumTarget.readLineCapped(ByteArray(0).inputStream()))
    }

    @Test
    fun `literal hosts resolve without DNS and blanks are refused`() {
        assertEquals("203.0.113.9", StratumTarget.resolve("203.0.113.9")!!.hostAddress)
        assertNull(StratumTarget.resolve(""))
        assertNull(StratumTarget.resolve("   "))
    }
}
