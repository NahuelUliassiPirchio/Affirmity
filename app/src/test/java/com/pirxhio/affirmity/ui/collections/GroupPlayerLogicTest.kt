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
}
