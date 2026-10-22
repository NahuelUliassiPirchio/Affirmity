package com.pirxhio.affirmity.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityDailyFactMutationTest {
    @Test fun `latest mood preserves completion and healer facts for the same owner`() {
        val original = ActivityDailyFactEntity("local:A", 100, 4, "Old", true, true, false, 1_000)
        val revised = applyDailyFactChange(original, DailyFactChange.Mood(2, "Revised"))
        assertEquals(original.copy(moodValue = 2, note = "Revised"), revised)
    }
    @Test fun `completion flags are OR monotonic and cannot inherit another owner`() {
        val first = ActivityDailyFactEntity("local:A", 100, meditationDone = true, completionPresent = true)
        val completed = applyDailyFactChange(first, DailyFactChange.Affirmation)
        assertEquals(true, completed.meditationDone)
        assertEquals(true, completed.affirmationDone)
        val second = applyDailyFactChange(ActivityDailyFactEntity("local:B", 100), DailyFactChange.Affirmation)
        assertEquals(false, second.meditationDone)
        assertEquals("local:B", second.owner)
    }
    @Test fun `healer retry preserves original activation without creating completion`() {
        val first = applyDailyFactChange(ActivityDailyFactEntity("local:A", 100), DailyFactChange.Healer(1_000))
        assertEquals(first, applyDailyFactChange(first, DailyFactChange.Healer(2_000)))
        assertEquals(false, first.completionPresent)
        assertEquals(null, first.moodValue)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `invalid mood fails instead of persisting unsupported fact`() {
        applyDailyFactChange(ActivityDailyFactEntity("local:A", 100), DailyFactChange.Mood(6, null))
    }
}
