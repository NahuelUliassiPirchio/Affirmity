package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakHealerStatsTest {

    // A fixed epochDay anchor used across tests (pattern: DailyCompletionStatsTest).
    private val start = 100L

    private fun fullDay(epochDay: Long) =
        DailyCompletionEntity(epochDay = epochDay, meditationDone = true, affirmationDone = true)

    private fun partialDay(epochDay: Long, meditationDone: Boolean = false, affirmationDone: Boolean = false) =
        DailyCompletionEntity(epochDay = epochDay, meditationDone = meditationDone, affirmationDone = affirmationDone)

    private fun use(healedEpochDay: Long) =
        StreakHealerUseEntity(healedEpochDay = healedEpochDay, activatedAtMillis = 0L)

    @Test
    fun `earning at 2 consecutive full days grants exactly 1 healer`() {
        val rows = listOf(fullDay(start), fullDay(start + 1))

        val state = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = start + 1, startEpochDay = start)

        assertTrue(state.healerHeld)
    }

    @Test
    fun `re-qualifying while already holding a healer does not grant a second one`() {
        val rows = (0..5).map { fullDay(start + it) }

        val state = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = start + 5, startEpochDay = start)

        assertTrue(state.healerHeld)
        assertEquals(6, state.generalStreakDays)
    }

    @Test
    fun `a zero-activity day breaks the general streak`() {
        val rows = listOf(partialDay(start, meditationDone = true))
        // day start + 1 has no row at all: zero activity

        val state = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = start + 1, startEpochDay = start)

        assertEquals(0, state.generalStreakDays)
    }

    @Test
    fun `activation on break plus one preserves continuity and is independent of that day's own completion`() {
        // day 100,101 full -> healer granted. day 102: zero activity (the break). day 103: window,
        // activated to heal day 102 -- with NO activity of its own on day 103.
        val rowsNoActivityOnWindowDay = listOf(fullDay(start), fullDay(start + 1))
        val uses = listOf(use(start + 2))

        val healedState = StreakHealerStats.evaluate(
            rowsNoActivityOnWindowDay,
            uses,
            todayEpochDay = start + 3,
            startEpochDay = start,
        )

        assertEquals(HealerActivation.UsedToday(start + 2), healedState.activation)
        assertTrue("healer must be consumed by activation", !healedState.healerHeld)
        // Activation covers ONLY the healed day (102). Day 103 has zero activity of its own, so the
        // general streak breaks again at day 103 regardless of the activation ("se rompe igual" — see
        // spec's "Explicit activation heals only day N; day N+1 follows normal rules").
        assertEquals(
            "activation must not cover day N+1 itself: zero activity on the window day still breaks the streak",
            0,
            healedState.generalStreakDays,
        )

        // With day 103 ALSO completed, the healed day 102 keeps the streak continuous through today.
        val rowsWithActivityOnWindowDay = listOf(fullDay(start), fullDay(start + 1), fullDay(start + 3))
        val continuousState = StreakHealerStats.evaluate(
            rowsWithActivityOnWindowDay,
            uses,
            todayEpochDay = start + 3,
            startEpochDay = start,
        )
        assertEquals(4, continuousState.generalStreakDays)
    }

    @Test
    fun `declining the window loses the streak but keeps the healer held for the next break`() {
        val rows = listOf(fullDay(start), fullDay(start + 1))
        // day 102 breaks (zero activity); window on day 103 passes unused.

        val declinedState = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = start + 4, startEpochDay = start)
        assertEquals(0, declinedState.generalStreakDays)
        assertTrue("an unused window must not forfeit the healer", declinedState.healerHeld)
        assertEquals(HealerActivation.Unavailable, declinedState.activation)

        // Later: the user resumes on day 105, breaks again on day 106 -> new window on day 107.
        val rowsWithRecovery = listOf(
            fullDay(start), fullDay(start + 1),
            partialDay(start + 5, meditationDone = true),
        )
        val reofferedState = StreakHealerStats.evaluate(
            rowsWithRecovery,
            emptyList(),
            todayEpochDay = start + 7,
            startEpochDay = start,
        )
        assertEquals(HealerActivation.Available(start + 6), reofferedState.activation)
        assertTrue(reofferedState.healerHeld)
    }

    @Test
    fun `the healer spend window is Available only on the single day right after the break`() {
        val rows = listOf(fullDay(start), fullDay(start + 1))
        // day 102 is the break (zero activity).

        val beforeWindow = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = start + 2, startEpochDay = start)
        assertEquals(HealerActivation.Unavailable, beforeWindow.activation)

        val duringWindow = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = start + 3, startEpochDay = start)
        assertEquals(HealerActivation.Available(start + 2), duringWindow.activation)

        val afterWindow = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = start + 4, startEpochDay = start)
        assertEquals(HealerActivation.Unavailable, afterWindow.activation)
    }

    @Test
    fun `days before startEpochDay never grant a healer, even though they still count toward the streak`() {
        // Full days 97..100, but startEpochDay floors *healer* evaluation at day 100 only.
        val rows = listOf(fullDay(start - 3), fullDay(start - 2), fullDay(start - 1), fullDay(start))

        val state = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = start, startEpochDay = start)

        // Default streakStartEpochDay == startEpochDay: with no wider streakStartEpochDay given,
        // the count itself is floored too (same value the caller passed in).
        assertEquals(1, state.generalStreakDays)
        assertTrue("a single full day at the floor must not grant a healer", !state.healerHeld)
    }

    @Test
    fun `streakStartEpochDay lets pre-rollout completions count toward the general streak`() {
        // Same 4 straight full days, but the general-streak count is given a wider, unfloored
        // window while the healer floor (startEpochDay) still sits at day 100 — the fix for the
        // "racha general shows 1 despite a week of history" report: the rollout floor should only
        // block retroactive healer grants/heals, not the visible day-count.
        val rows = listOf(fullDay(start - 3), fullDay(start - 2), fullDay(start - 1), fullDay(start))

        val state = StreakHealerStats.evaluate(
            rows,
            emptyList(),
            todayEpochDay = start,
            startEpochDay = start,
            streakStartEpochDay = start - 3,
        )

        assertEquals(4, state.generalStreakDays)
        assertTrue("a single full day at the healer floor must still not grant a healer", !state.healerHeld)
    }
}
