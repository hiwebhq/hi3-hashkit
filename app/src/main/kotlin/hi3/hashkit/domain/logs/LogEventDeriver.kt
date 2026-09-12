package hi3.hashkit.domain.logs

import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry

/**
 * Derives log-like event lines from consecutive telemetry snapshots, for firmwares that
 * expose no log stream (Avalon, Braiins OS, Antminer-class). Lines are worded so
 * [LogClassifier]'s severity prefixes ("E "/"W ") and category keywords land correctly,
 * letting the derived events flow through the same storage and [LogAnalyzer] as real
 * firmware logs. Pure and unit-testable.
 */
object LogEventDeriver {

    /** The telemetry facts an event can be derived from, captured per poll. */
    data class Snapshot(
        val status: MinerStatus,
        val uptimeSeconds: Long?,
        val chipTempC: Double?,
        val fanRpms: List<Int?>,
        val sharesAccepted: Long?,
        val sharesRejected: Long?,
        val poolUrl: String?,
    )

    fun snapshot(t: MinerTelemetry): Snapshot = Snapshot(
        status = t.status,
        uptimeSeconds = t.uptimeSeconds,
        chipTempC = t.chipTempC.value,
        fanRpms = t.fans.map { it.rpm },
        sharesAccepted = t.sharesAccepted,
        sharesRejected = t.sharesRejected,
        poolUrl = t.poolUrl,
    )

    /** How many rejected shares between two polls before it's worth an event line. */
    const val REJECT_SPIKE = 3

    /**
     * Event lines for the transition [prev] → [curr]; empty when nothing noteworthy
     * happened. The first poll after startup ([prev] == null) yields no events — there is
     * no baseline to compare against, and inventing one would fake a "recovery" on every
     * app launch.
     */
    fun derive(prev: Snapshot?, curr: Snapshot, chipTempLimitC: Double?): List<String> {
        prev ?: return emptyList()
        return listOfNotNull(
            statusLine(prev, curr),
            rebootLine(prev, curr),
            tempLine(prev, curr, chipTempLimitC),
            rejectLine(prev, curr),
            poolLine(prev, curr),
        ) + fanLines(prev, curr)
    }

    private fun statusLine(prev: Snapshot, curr: Snapshot): String? {
        if (curr.status == prev.status) return null
        val was = prev.status.name.lowercase()
        return when {
            curr.status == MinerStatus.OFFLINE ->
                "E events: connection lost — miner offline (was $was)"
            prev.status == MinerStatus.OFFLINE ->
                "events: connection restored — miner ${curr.status.name.lowercase()}"
            curr.status == MinerStatus.DEGRADED ->
                "W events: status changed $was → degraded"
            else ->
                "events: status changed $was → ${curr.status.name.lowercase()}"
        }
    }

    private fun rebootLine(prev: Snapshot, curr: Snapshot): String? {
        val was = prev.uptimeSeconds ?: return null
        val now = curr.uptimeSeconds ?: return null
        if (now >= was) return null
        return "W events: reboot detected — uptime reset (${was}s → ${now}s)"
    }

    private fun tempLine(prev: Snapshot, curr: Snapshot, limitC: Double?): String? {
        val limit = limitC ?: return null
        val was = prev.chipTempC ?: return null
        val now = curr.chipTempC ?: return null
        return when {
            was < limit && now >= limit ->
                "E events: chip temp %.1fC crossed the %.0fC limit".format(now, limit)
            was >= limit && now < limit ->
                "events: chip temp back under the limit (%.1fC)".format(now)
            else -> null
        }
    }

    private fun fanLines(prev: Snapshot, curr: Snapshot): List<String> =
        curr.fanRpms.mapIndexedNotNull { i, rpm ->
            val was = prev.fanRpms.getOrNull(i) ?: return@mapIndexedNotNull null
            if (was > 0 && rpm == 0) "E events: fan ${i + 1} stopped (0 rpm, was $was)" else null
        }

    private fun rejectLine(prev: Snapshot, curr: Snapshot): String? {
        val was = prev.sharesRejected ?: return null
        val now = curr.sharesRejected ?: return null
        if (now - was < REJECT_SPIKE) return null
        return "W events: ${now - was} shares rejected since the last poll"
    }

    private fun poolLine(prev: Snapshot, curr: Snapshot): String? {
        val was = prev.poolUrl ?: return null
        val now = curr.poolUrl ?: return null
        if (was == now) return null
        return "W events: pool changed $was → $now"
    }
}
