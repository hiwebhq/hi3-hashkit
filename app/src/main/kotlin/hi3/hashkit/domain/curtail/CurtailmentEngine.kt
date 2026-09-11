package hi3.hashkit.domain.curtail

/**
 * Pure decision core shared by solar-surplus and electricity-price curtailment. Given a live
 * signal and the current run state, it recommends whether to run, curtail, or hold — with a
 * hysteresis band (two thresholds) so the fleet doesn't flap around a single set-point.
 *
 * It only *recommends*; applying the action is a separate, explicit step. Nothing here touches
 * a miner or a plug.
 */
object CurtailmentEngine {

    enum class Action { RUN, CURTAIL, HOLD }

    /**
     * @param value the live signal (e.g. solar export in W, or price in p/kWh).
     * @param running whether the fleet is currently running (not curtailed).
     * @param resumeThreshold the value at which it becomes favourable to run again.
     * @param curtailThreshold the value at which it becomes unfavourable and we should curtail.
     * @param favourRunWhenAbove true when a *higher* value favours running (solar export);
     *        false when a *higher* value favours curtailing (electricity price).
     *
     * For a sensible hysteresis band, with favourRunWhenAbove the resume threshold should be
     * >= the curtail threshold; without it, resume should be <= curtail.
     */
    fun decide(
        value: Double,
        running: Boolean,
        resumeThreshold: Double,
        curtailThreshold: Double,
        favourRunWhenAbove: Boolean,
    ): Action {
        val runFavourable = if (favourRunWhenAbove) value >= resumeThreshold else value <= resumeThreshold
        val curtailFavourable = if (favourRunWhenAbove) value <= curtailThreshold else value >= curtailThreshold
        return when {
            running && curtailFavourable -> Action.CURTAIL
            !running && runFavourable -> Action.RUN
            else -> Action.HOLD
        }
    }

    /** Convenience: the recommended target state (running?) ignoring the current state. */
    fun targetRunning(
        value: Double,
        resumeThreshold: Double,
        curtailThreshold: Double,
        favourRunWhenAbove: Boolean,
    ): Boolean =
        if (favourRunWhenAbove) value >= resumeThreshold else value <= resumeThreshold
}
