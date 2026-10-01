package com.pirxhio.affirmity.ui.collections

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPlayerLogicTest {
    @Test
    fun `an empty group cannot be played`() {
        assertFalse(canPlayGroup(itemCount = 0))
    }

    @Test
    fun `a group with at least one affirmation can be played`() {
        assertTrue(canPlayGroup(itemCount = 1))
        assertTrue(canPlayGroup(itemCount = 12))
    }

    @Test
    fun `the player shows only when requested and the group still has affirmations`() {
        assertTrue(shouldShowGroupPlayer(requested = true, itemCount = 3))
        assertFalse(shouldShowGroupPlayer(requested = false, itemCount = 3))
    }

    @Test
    fun `the player closes itself when the group is emptied while playing`() {
        assertFalse(shouldShowGroupPlayer(requested = true, itemCount = 0))
    }
}
