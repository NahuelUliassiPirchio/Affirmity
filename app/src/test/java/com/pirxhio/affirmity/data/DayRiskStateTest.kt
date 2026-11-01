package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.analytics.DailyGoal
import org.junit.Test
import org.junit.Assert.assertEquals

class DayRiskStateTest {
    private val t = DayRiskEvaluator.DEFAULT_THRESHOLD_MINUTE

    private fun eval(med: Boolean, aff: Boolean, streak: Int, minute: Int) =
        DayRiskEvaluator.evaluate(med, aff, streak, minute)

    @Test fun bothDoneIsDoneEvenAfterThreshold() =
        assertEquals(DayRiskState.Done, eval(true, true, 5, t + 100))

    @Test fun beforeThresholdIsInProgressWithMissing() {
        assertEquals(DayRiskState.InProgress(setOf(DailyGoal.AFFIRMATION)), eval(true, false, 5, t - 1))
        assertEquals(
            DayRiskState.InProgress(setOf(DailyGoal.MEDITATION, DailyGoal.AFFIRMATION)),
            eval(false, false, 5, t - 1),
        )
    }

    @Test fun exactlyAtThresholdOneDoneIsGoalIncomplete() =
        assertEquals(DayRiskState.GoalIncomplete(DailyGoal.MEDITATION), eval(false, true, 0, t))

    @Test fun atThresholdNothingDoneWithStreakIsAtRisk() =
        assertEquals(DayRiskState.StreakAtRisk, eval(false, false, 3, t))

    @Test fun atThresholdNothingDoneWithoutStreakIsNotStarted() =
        assertEquals(DayRiskState.NotStarted, eval(false, false, 0, t))
}
