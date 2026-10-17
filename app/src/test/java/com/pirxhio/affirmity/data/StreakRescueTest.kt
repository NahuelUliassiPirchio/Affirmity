package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakRescueTest {

    private val start = 100L

    private fun use(healedEpochDay: Long) =
        StreakHealerUseEntity(healedEpochDay = healedEpochDay, activatedAtMillis = 0L)

    // ---- streakAtStakeDays -------------------------------------------------------------------

    @Test
    fun `generalStreakDays cannot stand in for the streak at stake while a rescue is offered`() {
        // Days 100..102 active, 103 missed, today = 104 with nothing logged yet. A healer is held
        // (two full pairs would be needed, so grant one through full days below).
        val full = { d: Long -> DailyCompletionEntity(d, meditationDone = true, affirmationDone = true) }
        val rows = listOf(full(100), full(101), full(102))
        val state = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = 104, startEpochDay = start)
        assertEquals(HealerActivation.Available(103), state.activation)
        // generalStreakDays walks from yesterday (the break day), so it reports 0 -- not 3.
        assertEquals(0, state.generalStreakDays)
        assertEquals(3, state.streakAtStakeDays)
    }

    @Test
    fun `streak at stake counts the run ending the day before the break`() {
        val full = { d: Long -> DailyCompletionEntity(d, meditationDone = true, affirmationDone = true) }
        val rows = (100L..105L).map(full)
        // break day 106, today 107
        val state = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = 107, startEpochDay = start)
        assertEquals(HealerActivation.Available(106), state.activation)
        assertEquals(6, state.streakAtStakeDays)
    }

    @Test
    fun `streak at stake counts healed days inside the run as done`() {
        val full = { d: Long -> DailyCompletionEntity(d, meditationDone = true, affirmationDone = true) }
        // 100,101 full (healer granted), 102 healed, 103 full, 104 full; break 105; today 106.
        val rows = listOf(full(100), full(101), full(103), full(104))
        val state = StreakHealerStats.evaluate(rows, listOf(use(102)), todayEpochDay = 106, startEpochDay = start)
        assertEquals(HealerActivation.Available(105), state.activation)
        assertEquals(5, state.streakAtStakeDays)
    }

    @Test
    fun `streak at stake of one when only the day before the break was active`() {
        val full = { d: Long -> DailyCompletionEntity(d, meditationDone = true, affirmationDone = true) }
        // 90,91 full earn a healer; 92 missed (never healed); 93 active; break 94; today 95.
        val rows = listOf(full(90), full(91), full(93))
        val state = StreakHealerStats.evaluate(rows, emptyList(), todayEpochDay = 95, startEpochDay = 90)
        assertEquals(HealerActivation.Available(94), state.activation)
        assertEquals(1, state.streakAtStakeDays)
    }

    @Test
    fun `streak at stake is zero when no rescue is on offer`() {
        val state = StreakHealerStats.evaluate(emptyList(), emptyList(), todayEpochDay = 104, startEpochDay = start)
        assertEquals(0, state.streakAtStakeDays)
    }

    @Test
    fun `streak at stake is zero when the break day is the first tracked day`() {
        val state = StreakHealerStats.streakEndingAt(
            endEpochDay = 99, streakStartEpochDay = 100, isDone = { true },
        )
        assertEquals(0, state)
    }

    @Test
    fun `streakEndingAt stops at the first not-done day and honours the floor`() {
        val done = setOf(97L, 98L, 99L)
        assertEquals(3, StreakHealerStats.streakEndingAt(99, 90, done::contains))
        assertEquals(2, StreakHealerStats.streakEndingAt(99, 98, done::contains))
        assertEquals(1, StreakHealerStats.streakEndingAt(97, 90, done::contains))
    }

    // ---- shouldShowStreakRescue --------------------------------------------------------------

    private val available = HealerActivation.Available(breakEpochDay = 99)
    private val today = 100L

    private fun show(
        activation: HealerActivation = available,
        healerCount: Int = 1,
        dismissed: Long? = null,
        fromNotification: Boolean = false,
        otherGateActive: Boolean = false,
    ) = shouldShowStreakRescue(activation, healerCount, dismissed, today, fromNotification, otherGateActive)

    @Test
    fun `shows when available with a healer and nothing blocks it`() {
        assertTrue(show())
        assertTrue(show(dismissed = today - 1))
    }

    @Test
    fun `hidden unless activation is available`() {
        assertFalse(show(activation = HealerActivation.Unavailable))
        assertFalse(show(activation = HealerActivation.UsedToday(99)))
        assertFalse(show(activation = HealerActivation.Unavailable, fromNotification = true))
    }

    @Test
    fun `hidden without a healer even from a notification`() {
        assertFalse(show(healerCount = 0))
        assertFalse(show(healerCount = 0, fromNotification = true))
    }

    @Test
    fun `hidden after dismissal today unless launched from the healer notification`() {
        assertFalse(show(dismissed = today))
        assertTrue(show(dismissed = today, fromNotification = true))
    }

    @Test
    fun `other gates always suppress it`() {
        assertFalse(show(otherGateActive = true))
        assertFalse(show(otherGateActive = true, fromNotification = true))
        assertFalse(show(otherGateActive = true, dismissed = today - 5))
    }
}
