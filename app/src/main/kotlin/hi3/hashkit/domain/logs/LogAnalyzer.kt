package hi3.hashkit.domain.logs

import androidx.annotation.StringRes
import hi3.hashkit.R
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

    data class Finding(
        val level: FindingLevel,
        @StringRes val titleRes: Int,
        @StringRes val suggestionRes: Int,
        /** Positional args for [titleRes], resolved at render time. */
        val titleArgs: List<Any> = emptyList(),
    )

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

    @Suppress("LongMethod") // flat checklist of independent findings; resource-ID conversion added lines, not logic
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
                R.string.logan_disconnects_title,
                R.string.logan_disconnects_suggestion,
                listOf(disconnects),
            )
        }

        val restarts = countText("restart", "rebooting", "brownout", "watchdog", "abort()") +
            classified.count { it.category == Category.SYSTEM && it.text.lowercase().let { t -> "reset" in t || "boot" in t } }
        if (restarts >= 2) {
            findings += Finding(
                FindingLevel.ERROR,
                R.string.logan_restarts_title,
                R.string.logan_restarts_suggestion,
                listOf(restarts),
            )
        }

        val thermal = classified.count {
            it.category == Category.THERMAL && it.severity != Severity.INFO
        }
        if (thermal >= 1) {
            findings += Finding(
                FindingLevel.WARN,
                R.string.logan_thermal_title,
                R.string.logan_thermal_suggestion,
                listOf(thermal),
            )
        }

        val asicErrors = classified.count { it.category == Category.ASIC && it.severity == Severity.ERROR }
        if (asicErrors >= 1) {
            findings += Finding(
                FindingLevel.ERROR,
                R.string.logan_asic_title,
                R.string.logan_asic_suggestion,
                listOf(asicErrors),
            )
        }

        val rejects = countText("reject")
        if (rejects >= 5) {
            findings += Finding(
                FindingLevel.WARN,
                R.string.logan_rejects_title,
                R.string.logan_rejects_suggestion,
                listOf(rejects),
            )
        }

        if (findings.isEmpty()) {
            findings += if (errors == 0) Finding(
                FindingLevel.INFO,
                R.string.logan_clean_title,
                R.string.logan_none_suggestion,
            ) else Finding(
                FindingLevel.INFO,
                R.string.logan_errors_no_pattern_title,
                R.string.logan_none_suggestion,
                listOf(errors),
            )
        }
        return findings
    }
}
