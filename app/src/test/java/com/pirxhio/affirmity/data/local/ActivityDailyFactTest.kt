package com.pirxhio.affirmity.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityDailyFactTest {
    @Test fun `empty adoption does not invent facts`() {
        assertEquals(emptyList<ActivityDailyFactEntity>(), adoptDailyFacts("local:first", emptyList(), emptyList(), emptyList()))
    }

    @Test fun `adoption retains out of window mood completion and healer without synthetic metrics`() {
        val facts = adoptDailyFacts("local:first", listOf(DailyMoodEntity(100, 4, "Retained")),
            listOf(DailyCompletionEntity(100, true, false), DailyCompletionEntity(101, false, false)),
            listOf(StreakHealerUseEntity(99, 1_000)))
        assertEquals(listOf(99L, 100L, 101L), facts.map { it.epochDay })
        assertEquals(4, facts[1].moodValue)
        assertEquals("Retained", facts[1].note)
        assertEquals(true, facts[1].meditationDone)
        assertEquals(true, facts[2].completionPresent)
        assertEquals(false, facts[2].affirmationDone)
        assertEquals(1_000L, facts[0].healedAtMillis)
        assertEquals(setOf("local:first"), facts.map { it.owner }.toSet())
    }
}
