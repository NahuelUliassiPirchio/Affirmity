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
    fun `partial missing and healed days break earning pairs but preserve OR continuity`() {
        val interruptions = listOf(
            listOf(partialDay(start + 1, meditationDone = true)) to emptyList(),
            listOf(partialDay(start + 1, affirmationDone = true)) to emptyList(),
            emptyList<DailyCompletionEntity>() to emptyList(),
            emptyList<DailyCompletionEntity>() to listOf(use(start + 1)),
        )
        interruptions.forEachIndexed { index, (middleRows, uses) ->
            val state = StreakHealerStats.evaluate(
                listOf(fullDay(start), fullDay(start + 2)) + middleRows, uses, start + 2, start,
            )
            assertEquals(0, state.healerCount)
            assertEquals(1, state.pairProgress)
            assertEquals(if (index == 2) 1 else 3, state.generalStreakDays)
        }
    }

    @Test
    fun `capacity banks no credit and activation day starts a fresh pair in either order`() {
        val fullRun = (0..8).map { fullDay(start + it) }
        val atCap = StreakHealerStats.evaluate(fullRun, emptyList(), start + 8, start)
        assertEquals(2, atCap.healerCount)
        assertEquals(0, atCap.pairProgress)
        val uses = listOf(use(start + 9))
        val freshDay = fullRun + fullDay(start + 10)
        // Completing before activation and activating before completion converge on the same log.
        val completionFirst = StreakHealerStats.evaluate(freshDay, emptyList(), start + 10, start)
        assertEquals(2, completionFirst.healerCount)
        val afterActivation = StreakHealerStats.evaluate(freshDay, uses, start + 10, start)
        val activationFirst = StreakHealerStats.evaluate(fullRun, uses, start + 10, start)
        assertEquals(1, activationFirst.healerCount)
        assertEquals(0, activationFirst.pairProgress)
        assertEquals(1, afterActivation.healerCount)
        assertEquals(1, afterActivation.pairProgress)
        assertEquals(afterActivation, StreakHealerStats.evaluate(freshDay, uses, start + 10, start))
        val refilled = StreakHealerStats.evaluate(freshDay + fullDay(start + 11), uses, start + 11, start)
        assertEquals(2, refilled.healerCount)
        assertEquals(0, refilled.pairProgress)
    }

    @Test
    fun `two successive uses spend one each and duplicate keys never spend twice`() {
        val rows = (0..3).map { fullDay(start + it) }
        val firstUse = listOf(use(start + 4), use(start + 4))
        val first = StreakHealerStats.evaluate(rows, firstUse, start + 5, start)
        assertEquals(1, first.healerCount)
        assertEquals(HealerActivation.UsedToday(start + 4), first.activation)
        val nextWindow = StreakHealerStats.evaluate(rows, firstUse, start + 6, start)
        assertEquals(HealerActivation.Available(start + 5), nextWindow.activation)
        val second = StreakHealerStats.evaluate(rows, firstUse + use(start + 5), start + 6, start)
        assertEquals(0, second.healerCount)
        assertEquals(6, second.generalStreakDays)
        assertEquals(HealerActivation.UsedToday(start + 5), second.activation)
        assertEquals(setOf(start + 4, start + 5), second.healedDays)
    }

    @Test
    fun `orphan and conflicting historical uses retain continuity without debt or earning`() {
        val state = StreakHealerStats.evaluate(
            listOf(fullDay(start), fullDay(start + 1), fullDay(start + 2)),
            listOf(use(start), use(start + 1), use(start + 1)), start + 2, start,
        )
        assertEquals(0, state.healerCount)
        assertEquals(1, state.pairProgress)
        assertEquals(3, state.generalStreakDays)
        assertEquals(setOf(start, start + 1), state.healedDays)
    }

    @Test
    fun `earning bounds are inclusive and ignore earlier completions`() {
        val today = StreakHealerStats.EPOCH_START_DAY + 400
        val floor = StreakHealerStats.healerStartEpochDay(today)
        assertEquals(today - 370, floor)
        assertEquals(StreakHealerStats.EPOCH_START_DAY, StreakHealerStats.healerStartEpochDay(StreakHealerStats.EPOCH_START_DAY + 3))
        val rows = (-2..3).map { fullDay(floor + it) }
        assertEquals(0, StreakHealerStats.evaluate(rows, emptyList(), floor, floor).healerCount)
        assertEquals(2, StreakHealerStats.evaluate(rows, emptyList(), today, floor).healerCount)
    }

    @Test
    fun `four complete days yield balances zero one one two`() {
        val rows = (0..3).map { fullDay(start + it) }
        val states = (0..3).map {
            StreakHealerStats.evaluate(rows, emptyList(), start + it, start)
        }
        assertEquals(listOf(0, 1, 1, 2), states.map { it.healerCount })
        assertEquals(listOf(1, 0, 1, 0), states.map { it.pairProgress })
    }

    @Test
    fun `earning at 2 consecutive full days grants exactly 1 healer`() {
        val rows = listOf(fullDay(start), fullDay(start + 1))

        val state = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = start + 1, startEpochDay = start)

        assertTrue(state.healerHeld)
    }

    @Test
    fun `complete days award non-overlapping pairs up to two healers`() {
        val rows = (0..5).map { fullDay(start + it) }

        val state = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = start + 5, startEpochDay = start)

        assertTrue(state.healerHeld)
        assertEquals(2, state.healerCount)
        assertEquals(0, state.pairProgress)
        assertEquals(6, state.generalStreakDays)
    }

    @Test
    fun `today not being done yet does not zero the streak, it just isn't counted`() {
        val rows = listOf(partialDay(start, meditationDone = true))
        // day start + 1 has no row at all: zero activity, but not yet "broken" -- it's still today.

        val state = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = start + 1, startEpochDay = start)

        assertEquals(1, state.generalStreakDays)
        assertTrue(!state.isTodayDone)
    }

    @Test
    fun `a full zero-activity day that is no longer today breaks the general streak`() {
        val rows = listOf(fullDay(start))
        // day start + 1 had zero activity and is now in the past; day start + 2 is "today".

        val state = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = start + 2, startEpochDay = start)

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
        // Activation covers ONLY the healed day (102). Day 103 (today) has zero activity of its own,
        // so it isn't counted -- but that alone doesn't zero the streak: the healed day 102 and the
        // two full days before it still count "through yesterday" (see spec's "Explicit activation
        // heals only day N; day N+1 follows normal rules").
        assertTrue("today's own zero activity must not count yet", !healedState.isTodayDone)
        assertEquals(
            "the streak through yesterday (the healed day) must still show, not reset to 0",
            3,
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
