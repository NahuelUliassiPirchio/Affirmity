package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.analytics.DailyGoal

/**
 * How "at risk" today's progress is, for motivational feedback. Pure, no I/O; mirror of the
 * server-side streak alert timing.
 */
sealed interface DayRiskState {
    /** Both daily goals (meditation and affirmations) are complete. */
    data object Done : DayRiskState

    /** Before the threshold and not both done; [missing] lists the goals still open. */
    data class InProgress(val missing: Set<DailyGoal>) : DayRiskState

    /**
     * At/after the threshold with exactly one goal done. The streak is SAFE (it needs only one
     * activity), so this is the softer level: only [missing] is left for a full day.
     */
    data class GoalIncomplete(val missing: DailyGoal) : DayRiskState

    /** At/after the threshold, nothing done today AND a streak (> 0) would be lost. */
    data object StreakAtRisk : DayRiskState

    /** At/after the threshold, nothing done today, but no streak to lose ("nothing to lose"). */
    data object NotStarted : DayRiskState
}

object DayRiskEvaluator {

    /**
     * Local minute-of-day at which a missing day becomes risky. Keep in sync with
     * STREAK_ALERT_MINUTE (20:00 user-local) in functions/src/planner.ts.
     */
    const val DEFAULT_THRESHOLD_MINUTE = 20 * 60

    /**
     * @param generalStreakDays healer-aware streak through yesterday (today not yet counted)
     * @param minuteOfDay device-local minute of day (0-1439)
     */
    fun evaluate(
        meditationDone: Boolean,
        affirmationDone: Boolean,
        generalStreakDays: Int,
        minuteOfDay: Int,
        thresholdMinute: Int = DEFAULT_THRESHOLD_MINUTE,
    ): DayRiskState {
        if (meditationDone && affirmationDone) return DayRiskState.Done
        val missing = buildSet {
            if (!meditationDone) add(DailyGoal.MEDITATION)
            if (!affirmationDone) add(DailyGoal.AFFIRMATION)
        }
        if (minuteOfDay < thresholdMinute) return DayRiskState.InProgress(missing)
        return when {
            missing.size == 1 -> DayRiskState.GoalIncomplete(missing.first())
            generalStreakDays > 0 -> DayRiskState.StreakAtRisk
            else -> DayRiskState.NotStarted
        }
    }
}
