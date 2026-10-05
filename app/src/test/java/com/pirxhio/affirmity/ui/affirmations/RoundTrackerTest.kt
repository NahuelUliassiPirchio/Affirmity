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
    }

    @Test
    fun `a forward fast fling counts the cards it passed over`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        assertFalse(tracker.onSettled(ids, 0))
        assertFalse(tracker.onSettled(ids, 4)) // seen: 0, 1..3 skipped, 4
        assertEquals(5, tracker.seenCount)
        // 5..8 flew past on the way to 9: the round completes on this single settle.
        assertTrue(tracker.onSettled(ids, 9))
    }

    @Test
    fun `a backward swipe counts only the landing card`() {
        val tracker = RoundTracker()
        val ids = feed(12)
        tracker.onSettled(ids, 8)
        tracker.onSettled(ids, 2)
        assertEquals(2, tracker.seenCount)
    }

    @Test
    fun `a jump of a full feed or more counts only the landing card`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        tracker.onSettled(ids, 0)
        tracker.onSettled(ids, 10) // exactly one feed length: not inferred
        assertEquals(1, tracker.seenCount)
        tracker.onSettled(ids, 35) // 25 pages: not inferred
        assertEquals(2, tracker.seenCount)
    }

    @Test
    fun `a fling across the wrap boundary counts the wrapped cards`() {
        val tracker = RoundTracker()
        val ids = feed(12)
        tracker.onSettled(ids, 10) // index 10
        tracker.onSettled(ids, 13) // pages 11, 12 -> indices 11, 0; landed on index 1
        assertEquals(4, tracker.seenCount)
    }

    @Test
    fun `the first settle after a feed change infers no intermediates`() {
        val tracker = RoundTracker()
        val ids = feed(12)
        tracker.onSettled(ids, 0)
        val other = List(12) { "b$it" }
        tracker.onSettled(other, 6) // would be a forward jump of 6 if the previous page leaked
        assertEquals(1, tracker.seenCount)
        tracker.onSettled(other, 9) // same feed again: 7, 8 inferred
        assertEquals(4, tracker.seenCount)
    }

    @Test
    fun `a fling that completes the round fires exactly once and seeds the next round`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        tracker.onSettled(ids, 0)
        assertTrue(tracker.onSettled(ids, 9))
        assertEquals(1, tracker.seenCount) // seeded with the landed id
        assertFalse(tracker.onSettled(ids, 9))
        // Next round: from page 9 forward-fling to page 18 (wraps over 0..7) lands on index 8.
        assertTrue(tracker.onSettled(ids, 18))
    }

    @Test
    fun `previous page memory is updated after completion`() {
        val tracker = RoundTracker()
        val ids = feed(10)
        tracker.onSettled(ids, 0)
        assertTrue(tracker.onSettled(ids, 9))
        // Adjacent forward move from the completing page infers nothing extra.
        assertFalse(tracker.onSettled(ids, 10))
        assertEquals(2, tracker.seenCount)
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
    fun `max round size is forty`() {
        assertEquals(40, MAX_ROUND_SIZE)
    }

    @Test
    fun `a 500 card feed completes after 40 distinct cards not 500`() {
        val tracker = RoundTracker()
        val ids = feed(500)
        (0 until 39).forEach { assertFalse("page $it", tracker.onSettled(ids, it)) }
        assertTrue(tracker.onSettled(ids, 39))
    }

    @Test
    fun `a 40 card feed completes at 40 and a 25 card feed at 25`() {
        val forty = RoundTracker()
        assertEquals(1, (0 until 40).count { forty.onSettled(feed(40), it) })
        val twentyFive = RoundTracker()
        val ids = feed(25)
        (0 until 24).forEach { assertFalse(twentyFive.onSettled(ids, it)) }
        assertTrue(twentyFive.onSettled(ids, 24))
    }

    @Test
    fun `a 41 card feed completes at 40 distinct cards`() {
        val tracker = RoundTracker()
        val ids = feed(41)
        (0 until 39).forEach { assertFalse(tracker.onSettled(ids, it)) }
        assertTrue(tracker.onSettled(ids, 39))
    }

    @Test
    fun `a capped round seeds the next one which also needs 40`() {
        val tracker = RoundTracker()
        val ids = feed(500)
        (0 until 40).forEach { tracker.onSettled(ids, it) }
        assertEquals(1, tracker.seenCount)
        // Seeded with a39; 38 more distinct cards are not enough, the 39th is.
        (40 until 78).forEach { assertFalse("page $it", tracker.onSettled(ids, it)) }
        assertTrue(tracker.onSettled(ids, 78))
    }

    @Test
    fun `a fling past the target fires once and seeds with the landed card`() {
        val tracker = RoundTracker()
        val ids = feed(500)
        assertFalse(tracker.onSettled(ids, 0))
        assertTrue(tracker.onSettled(ids, 100)) // 99 intermediates, far past 40
        assertEquals(1, tracker.seenCount)
        assertFalse(tracker.onSettled(ids, 100))
        assertFalse(tracker.onSettled(ids, 101))
    }

    @Test
    fun `a feed change resets a capped round`() {
        val tracker = RoundTracker()
        val ids = feed(500)
        (0 until 39).forEach { tracker.onSettled(ids, it) }
        val other = List(500) { "b$it" }
        assertFalse(tracker.onSettled(other, 39))
        assertEquals(1, tracker.seenCount)
    }

    @Test
    fun `the cap is injectable and the ten card minimum still applies`() {
        val small = RoundTracker(maxRoundSize = 12)
        val ids = feed(100)
        (0 until 11).forEach { assertFalse(small.onSettled(ids, it)) }
        assertTrue(small.onSettled(ids, 11))
        val nine = RoundTracker()
        assertEquals(0, (0 until 9).count { nine.onSettled(feed(9), it) })
    }

    @Test
    fun `a cap below the minimum is rejected`() {
        val result = runCatching { RoundTracker(maxRoundSize = MIN_ROUND_SIZE - 1) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
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

    /** Compose clamps currentPage to the last page when pageCount shrinks, so a shrunk feed leaves
     * the user parked on the final virtual page: forward swipes dead, backward ones fine. */
    @Test
    fun `recenters when a shrunk feed clamped the user onto the last virtual page`() {
        val feedSize = 5
        val count = feedSize * 10_000
        val target = recenteredPageOrNull(currentPage = count - 1, virtualPageCount = count, feedSize = feedSize)

        assertEquals(count / 2 - (count / 2) % feedSize + (count - 1) % feedSize, target)
        // Same card stays on screen: no visible jump.
        assertEquals((count - 1) % feedSize, target!! % feedSize)
    }

    @Test
    fun `leaves the page alone when there is room to scroll forward`() {
        assertEquals(null, recenteredPageOrNull(currentPage = 25_000, virtualPageCount = 50_000, feedSize = 5))
    }
}
