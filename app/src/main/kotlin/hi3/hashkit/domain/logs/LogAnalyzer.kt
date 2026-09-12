package hi3.hashkit.domain.logs

import hi3.hashkit.domain.logs.LogClassifier.Category
import hi3.hashkit.domain.logs.LogClassifier.Severity

/**
 * Heuristic, on-device log analysis over a window of classified ESP-Miner / AxeOS log
 * lines. Produces a summary (counts) and plain-language findings with suggested actions —
 * no cloud, no LLM. Pure and unit-testable.
 */
object LogAnalyzer {

    data class Summary(
        val total: Int,
        val errors: Int,
        val warnings: Int,
        val byCategory: Map<Category, Int>,
    )

    enum class FindingLevel { INFO, WARN, ERROR }

    data class Finding(val level: FindingLevel, val title: String, val suggestion: String)

    data class Analysis(
        val summary: Summary,
        val findings: List<Finding>,
        val classified: List<LogClassifier.Classified>,
    )

    fun analyze(lines: List<String>): Analysis {
        val classified = lines.map(LogClassifier::classify)
        val errors = classified.count { it.severity == Severity.ERROR }
        val warnings = classified.count { it.severity == Severity.WARN }
        val byCategory = Category.entries.associateWith { c -> classified.count { it.category == c } }
            .filterValues { it > 0 }

        val summary = Summary(classified.size, errors, warnings, byCategory)
        val findings = buildFindings(classified, errors)
        return Analysis(summary, findings, classified)
    }

    private fun buildFindings(classified: List<LogClassifier.Classified>, errors: Int): List<Finding> {
        val findings = mutableListOf<Finding>()
        // Lowercase each line once, not once per needle per call.
        val lowered = classified.map { it.text.lowercase() }
        fun countText(vararg needles: String) =
            lowered.count { line -> needles.any { it in line } }

        val disconnects = countText("disconnect", "connection closed", "reconnect", "socket error", "connection reset")
        if (disconnects >= 3) {
            findings += Finding(
                FindingLevel.WARN,
                "$disconnects pool/network disconnects in the recent log",
                "Check the pool URL/port and your Wi-Fi signal; a flaky link causes lost shares.",
            )
        }

        val restarts = countText("restart", "rebooting", "brownout", "watchdog", "abort()") +
            classified.count { it.category == Category.SYSTEM && it.text.lowercase().let { t -> "reset" in t || "boot" in t } }
        if (restarts >= 2) {
            findings += Finding(
                FindingLevel.ERROR,
                "Signs of $restarts restart(s) / resets",
                "Repeated restarts usually mean a power (PSU/USB) or thermal problem — check the supply and cooling.",
            )
        }

        val thermal = classified.count {
            it.category == Category.THERMAL && it.severity != Severity.INFO
        }
        if (thermal >= 1) {
            findings += Finding(
                FindingLevel.WARN,
                "$thermal thermal warning(s)/error(s)",
                "Improve airflow or lower the tune; sustained heat throttles hashrate and shortens hardware life.",
            )
        }

        val asicErrors = classified.count { it.category == Category.ASIC && it.severity == Severity.ERROR }
        if (asicErrors >= 1) {
            findings += Finding(
                FindingLevel.ERROR,
                "$asicErrors ASIC error(s)",
                "Persistent ASIC errors can indicate a failing chip/board — compare per-chip health and consider a reflash.",
            )
        }

        val rejects = countText("reject")
        if (rejects >= 5) {
            findings += Finding(
                FindingLevel.WARN,
                "$rejects rejected-share message(s)",
                "High rejects often mean stale work (network latency) or an over-aggressive tune.",
            )
        }

        if (findings.isEmpty()) {
            findings += Finding(
                FindingLevel.INFO,
                if (errors == 0) "No problems detected in the recent log." else "$errors error line(s), but no known problem pattern.",
                "Nothing actionable stood out. Keep the stream open to catch intermittent issues.",
            )
        }
        return findings
    }
}
