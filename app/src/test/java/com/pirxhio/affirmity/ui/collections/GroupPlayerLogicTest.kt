package com.pirxhio.affirmity.ui.collections

import com.pirxhio.affirmity.ui.affirmations.LOOP_MULTIPLIER
import com.pirxhio.affirmity.ui.affirmations.centeredStartPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `only a group with more than one item loops`() {
        assertFalse(groupPlayerLoops(0))
        assertFalse(groupPlayerLoops(1))
        assertTrue(groupPlayerLoops(2))
        assertTrue(groupPlayerLoops(40))
    }

    @Test
    fun `page count is virtual only when looping`() {
        assertEquals(0, groupPlayerPageCount(0))
        assertEquals(1, groupPlayerPageCount(1))
        assertEquals(2 * LOOP_MULTIPLIER, groupPlayerPageCount(2))
        assertEquals(7 * LOOP_MULTIPLIER, groupPlayerPageCount(7))
    }

    @Test
    fun `start page is the first item`() {
        assertEquals(0, groupPlayerStartPage(1))
        for (size in listOf(2, 5, 10, 13)) {
            assertEquals(0, groupPlayerStartPage(size) % size)
            assertEquals(centeredStartPage(size), groupPlayerStartPage(size))
        }
    }

    @Test
    fun `recenter never applies without a loop`() {
        assertNull(groupPlayerRecenterPageOrNull(currentPage = 0, itemCount = 0))
        assertNull(groupPlayerRecenterPageOrNull(currentPage = 0, itemCount = 1))
    }

    @Test
    fun `recenter is null in the middle of the range`() {
        val size = 5
        assertNull(groupPlayerRecenterPageOrNull(groupPlayerStartPage(size), size))
        assertNull(groupPlayerRecenterPageOrNull(size, size))
        assertNull(groupPlayerRecenterPageOrNull(groupPlayerPageCount(size) - size - 1, size))
    }

    @Test
    fun `recenter from the first cycle keeps the same item`() {
        val size = 5
        for (page in 0 until size) {
            val target = groupPlayerRecenterPageOrNull(page, size)
            assertEquals(centeredStartPage(size) + page % size, target)
            assertEquals(page % size, target!! % size)
        }
    }

    @Test
    fun `recenter from the last cycle keeps the same item`() {
        val size = 5
        val pageCount = groupPlayerPageCount(size)
        for (page in pageCount - size until pageCount) {
            val target = groupPlayerRecenterPageOrNull(page, size)
            assertEquals(page % size, target!! % size)
        }
    }

    @Test
    fun `boundary sizes open on item 0 inside the safe middle range`() {
        for (size in listOf(2, 9, 10, 41)) {
            val start = groupPlayerStartPage(size)
            assertEquals(size * LOOP_MULTIPLIER, groupPlayerPageCount(size))
            assertEquals(0, start % size)
            assertNull(groupPlayerRecenterPageOrNull(start, size))
        }
    }

    @Test
    fun `boundary sizes recenter from both edges onto the same item`() {
        for (size in listOf(2, 9, 10, 41)) {
            val pageCount = groupPlayerPageCount(size)
            for (page in listOf(0, size - 1, pageCount - size, pageCount - 1)) {
                val target = groupPlayerRecenterPageOrNull(page, size)
                assertEquals(page % size, target!! % size)
                assertNull(groupPlayerRecenterPageOrNull(target, size))
            }
        }
    }

    @Test
    fun `growing from one item recenters page 0 onto item 0`() {
        for (size in listOf(2, 9, 10, 41)) {
            assertEquals(centeredStartPage(size), groupPlayerRecenterPageOrNull(currentPage = 0, itemCount = size))
        }
    }

    @Test
    fun `shrinking to one item needs no recenter and keeps a single page`() {
        assertEquals(1, groupPlayerPageCount(1))
        assertEquals(0, groupPlayerStartPage(1))
        assertNull(groupPlayerRecenterPageOrNull(currentPage = 0, itemCount = 1))
    }
}
