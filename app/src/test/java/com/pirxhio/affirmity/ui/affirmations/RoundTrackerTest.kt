package com.pirxhio.affirmity.ui.affirmations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundTrackerTest {

    private fun feed(size: Int) = List(size) { "a$it" }

    @Test
    fun `min round size is ten`() {
        assertEquals(10, MIN_ROUND_SIZE)
    }

    @Test
    fun `completes exactly once when every id has been seen`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        val completions = (0 until 10).count { tracker.onSettled(ids, it) }
        assertEquals(1, completions)
    }

    @Test
    fun `does not complete before the last id is seen`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        (0 until 9).forEach { assertFalse(tracker.onSettled(ids, it)) }
        assertTrue(tracker.onSettled(ids, 9))
    }

    @Test
    fun `feeds smaller than the minimum never complete`() {
        val tracker = RoundTracker()
        val ids = feed(MIN_ROUND_SIZE - 1)
        repeat(3) { lap -> ids.indices.forEach { assertFalse("lap $lap", tracker.onSettled(ids, it)) } }
    }

    @Test
    fun `the initial page counts as seen`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        assertFalse(tracker.onSettled(ids, 0))
        // 9 more distinct swipes, not 10.
        val completions = (1 until 10).count { tracker.onSettled(ids, it) }
        assertEquals(1, completions)
    }

    @Test
    fun `backward swipes and revisits do not complete early`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        // Ping-pong between 0 and 1 many times: only two distinct ids.
        repeat(20) { assertFalse(tracker.onSettled(ids, it % 2)) }
        // Swipe forward through 2..8 (still one id missing), going back one step in between.
        (2..8).forEach {
            assertFalse(tracker.onSettled(ids, it))
            assertFalse(tracker.onSettled(ids, it - 1))
        }
        assertTrue(tracker.onSettled(ids, 9))
    }

    @Test
    fun `wrap-around revisiting an id does not complete early`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        (0 until 5).forEach { assertFalse(tracker.onSettled(ids, it)) }
        // Jump back to 0 (as a wrap would) then continue: 0 again adds nothing.
        assertFalse(tracker.onSettled(ids, 0))
        (5 until 9).forEach { assertFalse(tracker.onSettled(ids, it)) }
        assertTrue(tracker.onSettled(ids, 9))
    }

    @Test
    fun `the next round starts after completion and completes again`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        (0 until 10).forEach { tracker.onSettled(ids, it) }
        // Last item (9) seeds the next round; 9 more distinct ids finish it.
        (0 until 8).forEach { assertFalse(tracker.onSettled(ids, it)) }
        assertTrue(tracker.onSettled(ids, 8))
    }

    @Test
    fun `staying on the same page after completion does not complete again`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        (0 until 10).forEach { tracker.onSettled(ids, it) }
        repeat(5) { assertFalse(tracker.onSettled(ids, 9)) }
    }

    @Test
    fun `a changed feed resets progress`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        (0 until 9).forEach { tracker.onSettled(ids, it) }
        val reordered = ids.reversed()
        // Reorder (randomize toggled): progress is discarded, this settle is the new first one.
        assertFalse(tracker.onSettled(reordered, 0))
        assertFalse(tracker.onSettled(reordered, 1))
        (2 until 9).forEach { assertFalse(tracker.onSettled(reordered, it)) }
        assertTrue(tracker.onSettled(reordered, 9))
    }

    @Test
    fun `a shrinking feed resets safely and honors the new size`() {
        val tracker = RoundTracker()
        val ids = feed(12)
        (0 until 11).forEach { tracker.onSettled(ids, it) }
        val shrunk = ids.filterNot { it == "a3" } // user hid one: 11 left
        assertFalse(tracker.onSettled(shrunk, 0))
        (1 until 10).forEach { assertFalse(tracker.onSettled(shrunk, it)) }
        assertTrue(tracker.onSettled(shrunk, 10))
    }

    @Test
    fun `a feed shrinking below the minimum never completes`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        (0 until 9).forEach { tracker.onSettled(ids, it) }
        val shrunk = ids.drop(1) // 9 left
        shrunk.indices.forEach { assertFalse(tracker.onSettled(shrunk, it)) }
    }

    @Test
    fun `out of range pages and empty feeds are ignored`() {
        val tracker = RoundTracker()
        assertFalse(tracker.onSettled(emptyList(), 0))
        assertFalse(tracker.onSettled(feed(10), -1))
        assertFalse(tracker.onSettled(feed(10), 10))
    }

    @Test
    fun `exactly ten distinct ids completes and nine never does`() {
        val ten = RoundTracker()
        assertEquals(1, (0 until 10).count { ten.onSettled(feed(10), it) })
        val nine = RoundTracker()
        assertEquals(0, (0 until 9).count { nine.onSettled(feed(9), it) })
    }

    @Test
    fun `duplicate ids count once`() {
        // 12 entries but only 9 distinct ids: below the minimum, never completes.
        val dupes = List(12) { "a${it % 9}" }
        val tracker = RoundTracker()
        repeat(2) { dupes.indices.forEach { i -> assertFalse(tracker.onSettled(dupes, i)) } }

        // 12 entries, 10 distinct ids: completes once all 10 distinct were seen.
        val ten = List(12) { "b${minOf(it, 9)}" }
        val t2 = RoundTracker()
        val completions = ten.indices.count { t2.onSettled(ten, it) }
        assertEquals(1, completions)
    }

    @Test
    fun `a filter change under a stationary pager never completes a round`() {
        val tracker = RoundTracker()
        val before = feed(10)
        (0 until 9).forEach { tracker.onSettled(before, it) }
        // Filter swaps the feed for a different 10-item list while the user stays on index 9.
        val after = List(10) { "b$it" }
        assertFalse(tracker.onSettled(after, 9))
        assertFalse(tracker.onSettled(after, 9))
    }

    @Test
    fun `a refresh with identical ids keeps progress and does not complete early`() {
        val tracker = RoundTracker()
        (0 until 5).forEach { tracker.onSettled(feed(10), it) }
        // New list instance, same contents (e.g. refresh): still mid-round, nothing fires.
        assertFalse(tracker.onSettled(feed(10), 5))
        (6 until 9).forEach { assertFalse(tracker.onSettled(feed(10), it)) }
        assertTrue(tracker.onSettled(feed(10), 9))
    }

    @Test
    fun `a reorder under a stationary pager never completes a round`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        (0 until 9).forEach { tracker.onSettled(ids, it) }
        assertFalse(tracker.onSettled(ids.shuffled(java.util.Random(7)), 9))
    }

    @Test
    fun `settled index wraps around the virtual page range`() {
        assertEquals(2, settledAffirmationIndex(page = 2, feedSize = 10))
        assertEquals(2, settledAffirmationIndex(page = 10 * 3 + 2, feedSize = 10))
        assertEquals(7, settledAffirmationIndex(page = 10 * 5_000 + 7, feedSize = 10))
    }

    @Test
    fun `settled index is null for empty feeds and negative pages`() {
        assertEquals(null, settledAffirmationIndex(page = 3, feedSize = 0))
        assertEquals(null, settledAffirmationIndex(page = -1, feedSize = 10))
    }
}
