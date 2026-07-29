package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyCompletionStatsTest {

    // A fixed Monday epochDay used as the week anchor across tests.
    private val monday = 100L

    @Test
    fun `non-contiguous week keeps Monday completed even though Tuesday is empty`() {
        val rows = listOf(
            DailyCompletionEntity(epochDay = monday, meditationDone = true, affirmationDone = true),
            DailyCompletionEntity(epochDay = monday + 2, meditationDone = true, affirmationDone = true), // Wed
        )

        val weekly = DailyCompletionStats.toWeeklyStreak(
            rows = rows,
            weekStartEpochDay = monday,
            todayEpochDay = monday + 2,
            isDone = { it.meditationDone },
        )

        assertEquals(
            listOf(true, false, true, false, false, false, false),
            weekly.completedDays,
        )
    }

    @Test
    fun `streak resets to zero the day after a missed day`() {
        val rows = listOf(
            DailyCompletionEntity(epochDay = monday, meditationDone = true, affirmationDone = true),
            // Tuesday (monday + 1) missing entirely
            DailyCompletionEntity(epochDay = monday + 2, meditationDone = true, affirmationDone = true),
        )

        val streakOnTuesday = DailyCompletionStats.streakOf(
            rows = rows,
            todayEpochDay = monday + 1,
            isDone = { it.meditationDone },
        )

        assertEquals(0, streakOnTuesday)
    }

    @Test
    fun `streak counts contiguous completed days ending today`() {
        val rows = listOf(
            DailyCompletionEntity(epochDay = monday, meditationDone = true, affirmationDone = true),
            DailyCompletionEntity(epochDay = monday + 1, meditationDone = true, affirmationDone = true),
            DailyCompletionEntity(epochDay = monday + 2, meditationDone = true, affirmationDone = true),
        )

        val streakToday = DailyCompletionStats.streakOf(
            rows = rows,
            todayEpochDay = monday + 2,
            isDone = { it.meditationDone },
        )

        assertEquals(3, streakToday)
    }

    @Test
    fun `week-boundary day (Sunday) belongs to the correct 7-day window`() {
        val sunday = monday + 6
        val rows = listOf(
            DailyCompletionEntity(epochDay = sunday, meditationDone = true, affirmationDone = false),
        )

        val weekly = DailyCompletionStats.toWeeklyStreak(
            rows = rows,
            weekStartEpochDay = monday,
            todayEpochDay = sunday,
            isDone = { it.meditationDone },
        )

        assertEquals(
            listOf(false, false, false, false, false, false, true),
            weekly.completedDays,
        )
    }

    @Test
    fun `affirmationDone and meditationDone are tracked independently for the same day`() {
        val rows = listOf(
            DailyCompletionEntity(epochDay = monday, meditationDone = true, affirmationDone = false),
        )

        val meditationWeekly = DailyCompletionStats.toWeeklyStreak(
            rows = rows,
            weekStartEpochDay = monday,
            todayEpochDay = monday,
            isDone = { it.meditationDone },
        )
        val affirmationWeekly = DailyCompletionStats.toWeeklyStreak(
            rows = rows,
            weekStartEpochDay = monday,
            todayEpochDay = monday,
            isDone = { it.affirmationDone },
        )

        assertEquals(true, meditationWeekly.completedDays[0])
        assertEquals(false, affirmationWeekly.completedDays[0])
    }
}
