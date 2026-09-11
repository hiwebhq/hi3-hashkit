package hi3.hashkit.domain.logs

import hi3.hashkit.domain.logs.LogClassifier.Category
import hi3.hashkit.domain.logs.LogClassifier.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogAnalyzerTest {

    @Test fun classifiesSeverityFromEspPrefixes() {
        assertEquals(Severity.ERROR, LogClassifier.severity("E (12345) asic: init failed"))
        assertEquals(Severity.WARN, LogClassifier.severity("W (12345) net: retrying"))
        assertEquals(Severity.INFO, LogClassifier.severity("I (12345) system: ok"))
    }

    @Test fun classifiesCategoryByKeyword() {
        assertEquals(Category.THERMAL, LogClassifier.category("I (1) temp: 68C"))
        assertEquals(Category.POOL, LogClassifier.category("I (1) stratum: subscribed"))
        assertEquals(Category.SHARE, LogClassifier.category("I (1) share rejected"))
        assertEquals(Category.NETWORK, LogClassifier.category("W (1) wifi: disconnected"))
        assertEquals(Category.SYSTEM, LogClassifier.category("E (1) brownout reset"))
    }

    @Test fun flagsRepeatedRestarts() {
        val lines = List(3) { "E (100) system: brownout reset, rebooting" }
        val a = LogAnalyzer.analyze(lines)
        assertTrue(a.findings.any { it.title.contains("restart", true) && it.level == LogAnalyzer.FindingLevel.ERROR })
    }

    @Test fun flagsDisconnectStorm() {
        val lines = List(4) { "W (1) pool: connection closed, reconnect" }
        val a = LogAnalyzer.analyze(lines)
        assertTrue(a.findings.any { it.title.contains("disconnect", true) })
    }

    @Test fun cleanLogProducesNoProblemFinding() {
        val lines = listOf(
            "I (1) system: boot ok",
            "I (2) asic: hashrate nominal",
            "I (3) stratum: share accepted",
        )
        val a = LogAnalyzer.analyze(lines)
        assertEquals(0, a.summary.errors)
        assertTrue(a.findings.single().level == LogAnalyzer.FindingLevel.INFO)
    }

    @Test fun summaryCountsErrorsAndCategories() {
        val a = LogAnalyzer.analyze(
            listOf("E (1) asic: fail", "W (2) temp: high", "I (3) stratum: ok"),
        )
        assertEquals(3, a.summary.total)
        assertEquals(1, a.summary.errors)
        assertEquals(1, a.summary.warnings)
        assertTrue((a.summary.byCategory[Category.THERMAL] ?: 0) >= 1)
    }
}
