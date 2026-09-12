package hi3.hashkit.domain.logs

import hi3.hashkit.domain.model.MinerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogEventDeriverTest {

    private fun snap(
        status: MinerStatus = MinerStatus.ONLINE,
        uptime: Long? = 1000L,
        temp: Double? = 60.0,
        fans: List<Int?> = listOf(4000),
        accepted: Long? = 100L,
        rejected: Long? = 1L,
        pool: String? = "stratum+tcp://pool.example:3333",
    ) = LogEventDeriver.Snapshot(status, uptime, temp, fans, accepted, rejected, pool)

    @Test
    fun `first poll yields no events`() {
        assertTrue(LogEventDeriver.derive(null, snap(), 85.0).isEmpty())
    }

    @Test
    fun `steady state yields no events`() {
        assertTrue(LogEventDeriver.derive(snap(), snap(uptime = 1300L, accepted = 120L), 85.0).isEmpty())
    }

    @Test
    fun `offline transition is an error with network wording`() {
        val lines = LogEventDeriver.derive(snap(), snap(status = MinerStatus.OFFLINE), 85.0)
        assertEquals(1, lines.size)
        assertEquals(LogClassifier.Severity.ERROR, LogClassifier.severity(lines[0]))
        assertEquals(LogClassifier.Category.NETWORK, LogClassifier.category(lines[0]))
    }

    @Test
    fun `uptime reset reads as a system reboot`() {
        val lines = LogEventDeriver.derive(snap(uptime = 90_000L), snap(uptime = 45L), 85.0)
        assertEquals(1, lines.size)
        assertEquals(LogClassifier.Severity.WARN, LogClassifier.severity(lines[0]))
        assertEquals(LogClassifier.Category.SYSTEM, LogClassifier.category(lines[0]))
    }

    @Test
    fun `temp limit crossing is a thermal error, recovery is info`() {
        val up = LogEventDeriver.derive(snap(temp = 80.0), snap(temp = 87.5), 85.0)
        assertEquals(LogClassifier.Severity.ERROR, LogClassifier.severity(up.single()))
        assertEquals(LogClassifier.Category.THERMAL, LogClassifier.category(up.single()))
        val down = LogEventDeriver.derive(snap(temp = 87.5), snap(temp = 78.0), 85.0)
        assertEquals(LogClassifier.Severity.INFO, LogClassifier.severity(down.single()))
    }

    @Test
    fun `fan stop and reject spike are reported`() {
        val lines = LogEventDeriver.derive(
            snap(fans = listOf(4000, 4100), rejected = 1L),
            snap(fans = listOf(4000, 0), rejected = 9L),
            85.0,
        )
        assertEquals(2, lines.size)
        assertTrue(lines.any { LogClassifier.category(it) == LogClassifier.Category.THERMAL })
        assertTrue(lines.any { LogClassifier.category(it) == LogClassifier.Category.SHARE })
    }

    @Test
    fun `pool change is a pool-category warning`() {
        val lines = LogEventDeriver.derive(snap(), snap(pool = "stratum+tcp://other.example:3333"), 85.0)
        assertEquals(LogClassifier.Category.POOL, LogClassifier.category(lines.single()))
    }
}
