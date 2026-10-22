package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.*
import org.junit.Assert.*
import org.junit.Test

class ActivityMonthTest {
    @Test fun `mood-only historical days and legacy flags survive without inventing activity`() {
        val range = ActivityRangeState.Ready(mapOf(10L to aggregateActivityDay(10, emptyList(), ActivityCoverage.UNKNOWN)), 2)
        val month = joinActivityMonth(range, listOf(DailyMoodEntity(10, 5, "latest")),
            listOf(DailyCompletionEntity(10, true, false)), listOf(StreakHealerUseEntity(10, 100))) as ActivityMonthState.Ready
        val day = month.days.getValue(10)
        assertEquals(5, day.mood?.moodValue)
        assertTrue(day.completion?.meditationDone == true)
        assertTrue(day.healed)
        assertEquals(0, day.activity.sessions.size)
        assertEquals(ActivityCoverage.UNKNOWN, day.activity.coverage)
        assertEquals(2, month.pendingCount)
    }
    @Test fun `load failure and loading are not converted to empty success`() {
        val error = IllegalStateException("Offline")
        assertEquals(ActivityMonthState.Error(error), joinActivityMonth(ActivityRangeState.Error(error), emptyList(), emptyList(), emptyList()))
        assertEquals(ActivityMonthState.Loading, joinActivityMonth(ActivityRangeState.Loading, emptyList(), emptyList(), emptyList()))
    }
}
