package com.pirxhio.affirmity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class StreakMilestoneDetectorTest {

    private fun evaluate(stored: MilestoneProgress, days: Int, canCelebrate: Boolean = true) =
        StreakMilestoneDetector.evaluate(stored, days, canCelebrate)

    @Test
    fun `first observed value seeds both fields without celebrating`() {
        val decision = evaluate(MilestoneProgress(), days = 14, canCelebrate = true)

        assertNull(decision.celebrate)
        assertEquals(MilestoneProgress(highestCelebrated = 14, bestReached = 14), decision.updated)
    }

    @Test
    fun `seeding with no milestone reached stores zero so it is not re-seeded`() {
        val decision = evaluate(MilestoneProgress(), days = 0)

        assertEquals(MilestoneProgress(0, 0), decision.updated)
    }

    @Test
    fun `reaching 7 after 6 celebrates 7 and persists it`() {
        val decision = evaluate(MilestoneProgress(3, 3), days = 7)

        assertEquals(7, decision.celebrate)
        assertEquals(MilestoneProgress(7, 7), decision.updated)
    }

    @Test
    fun `day 1 is badge-only and never celebrates but updates the best`() {
        val decision = evaluate(MilestoneProgress(0, 0), days = 1)

        assertNull(decision.celebrate)
        assertEquals(MilestoneProgress(0, 1), decision.updated)
    }

    @Test
    fun `jumping past several milestones celebrates only the highest`() {
        val decision = evaluate(MilestoneProgress(0, 0), days = 31)

        assertEquals(30, decision.celebrate)
        assertEquals(MilestoneProgress(30, 30), decision.updated)
    }

    @Test
    fun `re-reaching a milestone after a break does not celebrate again`() {
        val afterBreak = evaluate(MilestoneProgress(14, 14), days = 3)
        assertNull(afterBreak.celebrate)
        assertEquals(MilestoneProgress(14, 14), afterBreak.updated)

        val reachedAgain = evaluate(afterBreak.updated, days = 7)
        assertNull(reachedAgain.celebrate)
        assertEquals(MilestoneProgress(14, 14), reachedAgain.updated)
    }

    @Test
    fun `same milestone is not celebrated twice on repeated emissions`() {
        val first = evaluate(MilestoneProgress(3, 3), days = 7)
        val second = evaluate(first.updated, days = 7)

        assertNull(second.celebrate)
    }

    @Test
    fun `best reached only ever grows`() {
        val decision = evaluate(MilestoneProgress(30, 30), days = 2)

        assertEquals(30, decision.updated.bestReached)
    }

    @Test
    fun `non-celebrating emission catches up silently so it never replays later`() {
        val silent = evaluate(MilestoneProgress(3, 3), days = 7, canCelebrate = false)
        assertNull(silent.celebrate)
        assertEquals(MilestoneProgress(7, 7), silent.updated)

        assertNull(evaluate(silent.updated, days = 7).celebrate)
    }

    private fun state(days: Int, todayDone: Boolean, healers: Int = 0) = StreakHealerState(
        generalStreakDays = days,
        isTodayDone = todayDone,
        healerCount = healers,
        pairProgress = 0,
        healedDays = emptySet(),
        activation = HealerActivation.Unavailable,
    )

    @Test
    fun `cache then server jump is not a genuine increment and seeds silently`() {
        val cache = state(0, todayDone = false)
        val server = state(30, todayDone = true)
        assertFalse(StreakMilestoneDetector.isGenuineStreakIncrement(null, cache))
        assertFalse(StreakMilestoneDetector.isGenuineStreakIncrement(cache, server))
        val decision = StreakMilestoneDetector.evaluate(
            MilestoneProgress(7, 14), 30,
            StreakMilestoneDetector.isGenuineStreakIncrement(cache, server),
        )
        assertNull(decision.celebrate)
        assertEquals(30, decision.updated.highestCelebrated)
    }

    @Test
    fun `plus one with today done is a genuine increment`() {
        assertTrue(StreakMilestoneDetector.isGenuineStreakIncrement(state(6, false), state(7, true)))
    }

    @Test
    fun `plus one without today done is not a genuine increment`() {
        assertFalse(StreakMilestoneDetector.isGenuineStreakIncrement(state(6, true), state(7, false)))
    }

    @Test
    fun `midnight re-subscription keeps count equal and never celebrates`() {
        val beforeMidnight = state(7, todayDone = true)
        val afterMidnight = state(7, todayDone = false)
        assertFalse(StreakMilestoneDetector.isGenuineStreakIncrement(beforeMidnight, afterMidnight))
    }

    @Test
    fun `completing right after midnight still celebrates against yesterday value`() {
        // prev emission: new day, nothing done yet, count still N (through yesterday)
        assertTrue(StreakMilestoneDetector.isGenuineStreakIncrement(state(6, false), state(7, true)))
    }

    @Test
    fun `healer grant requires exactly plus one`() {
        assertTrue(StreakMilestoneDetector.isGenuineHealerGrant(state(5, true, healers = 0), state(5, true, healers = 1)))
        assertFalse(StreakMilestoneDetector.isGenuineHealerGrant(state(5, true, healers = 0), state(5, true, healers = 2)))
        assertFalse(StreakMilestoneDetector.isGenuineHealerGrant(null, state(5, true, healers = 1)))
        assertFalse(StreakMilestoneDetector.isGenuineHealerGrant(state(5, true, healers = 2), state(5, true, healers = 1)))
    }
}
