package com.pirxhio.affirmity.personalization.goals

import org.junit.Assert.assertEquals
import org.junit.Test

class DivergenceDismissalStorageTest {

    @Test
    fun `once and twice sets decode to capped per-goal counts`() {
        assertEquals(
            mapOf("calm" to 1, "direction" to 2),
            divergenceDismissalCounts(
                dismissedOnceGoalIds = setOf("calm", "direction"),
                dismissedTwiceGoalIds = setOf("direction"),
            ),
        )
    }

    @Test
    fun `successive dismissals move a goal from once to twice and remain capped`() {
        val first = nextDivergenceDismissalTiers("calm", emptySet(), emptySet())
        val second = nextDivergenceDismissalTiers("calm", first.onceGoalIds, first.twiceGoalIds)
        val third = nextDivergenceDismissalTiers("calm", second.onceGoalIds, second.twiceGoalIds)

        assertEquals(DivergenceDismissalTiers(setOf("calm"), emptySet()), first)
        assertEquals(DivergenceDismissalTiers(setOf("calm"), setOf("calm")), second)
        assertEquals(second, third)
    }
}
