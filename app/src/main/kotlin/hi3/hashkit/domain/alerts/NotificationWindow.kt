package hi3.hashkit.domain.alerts

/**
 * Pure time-window logic for quiet hours and the daily digest, unit-testable without a
 * clock. Minutes are minutes-of-day in the device's local timezone (0..1439).
 */
object NotificationWindow {

    /**
     * Is [nowMinuteOfDay] inside the quiet window [startMin, endMin)? Supports windows
     * that wrap past midnight (e.g. 22:00 → 07:00). start == end means "no quiet time".
     */
    fun isQuiet(nowMinuteOfDay: Int, startMin: Int, endMin: Int): Boolean {
        if (startMin == endMin) return false
        return if (startMin < endMin) {
            nowMinuteOfDay in startMin until endMin
        } else {
            nowMinuteOfDay >= startMin || nowMinuteOfDay < endMin
        }
    }

    /**
     * Should the daily digest fire now? True when the local time has reached the digest
     * hour today and the last digest was sent before today's digest instant.
     *
     * @param nowEpochMs current time
     * @param todayDigestEpochMs epoch millis of today's digest hour, in local time
     * @param lastSentEpochMs when the digest last fired (0 = never)
     */
    fun digestDue(nowEpochMs: Long, todayDigestEpochMs: Long, lastSentEpochMs: Long): Boolean =
        nowEpochMs >= todayDigestEpochMs && lastSentEpochMs < todayDigestEpochMs
}
