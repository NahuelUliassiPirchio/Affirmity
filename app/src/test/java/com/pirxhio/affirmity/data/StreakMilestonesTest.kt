package com.pirxhio.affirmity.data

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class StreakMilestonesTest {
    @Test fun exactHitVsBetween() {
        assertEquals(7, StreakMilestones.milestoneReachedAt(7))
        assertNull(StreakMilestones.milestoneReachedAt(8))
        assertNull(StreakMilestones.milestoneReachedAt(0))
    }

    @Test fun highestReachedBoundaries() {
        assertNull(StreakMilestones.highestReached(0))
        assertEquals(1, StreakMilestones.highestReached(2))
        assertEquals(30, StreakMilestones.highestReached(59))
        assertEquals(365, StreakMilestones.highestReached(400))
    }

    @Test fun nextBoundaries() {
        assertEquals(1, StreakMilestones.next(0))
        assertEquals(14, StreakMilestones.next(7))
        assertNull(StreakMilestones.next(365))
    }
}
