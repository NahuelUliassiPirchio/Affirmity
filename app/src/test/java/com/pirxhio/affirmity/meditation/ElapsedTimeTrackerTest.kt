package com.pirxhio.affirmity.meditation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElapsedTimeTrackerTest {

    private class FakeTimeSource(private var current: Long = 0L) : MonotonicTimeSource {
        fun advanceBy(millis: Long) {
            current += millis
        }
        override fun nowMillis(): Long = current
    }

    @Test
    fun `elapsed is zero right after start`() {
        val tracker = ElapsedTimeTracker(FakeTimeSource())

        tracker.start(durationMillis = 10_000)

        assertEquals(0L, tracker.elapsedMillis())
    }

    @Test
    fun `elapsed follows the time source, not a tick count`() {
        val time = FakeTimeSource()
        val tracker = ElapsedTimeTracker(time)
        tracker.start(durationMillis = 10_000)

        // A single, large jump — as if the process had been backgrounded — is reflected exactly,
        // with no dependency on how many ticks "should" have happened.
        time.advanceBy(7_500)

        assertEquals(7_500L, tracker.elapsedMillis())
        assertEquals(2_500L, tracker.remainingMillis())
    }

    @Test
    fun `remaining never goes negative past the duration`() {
        val time = FakeTimeSource()
        val tracker = ElapsedTimeTracker(time)
        tracker.start(durationMillis = 1_000)

        time.advanceBy(5_000)

        assertEquals(0L, tracker.remainingMillis())
        assertTrue(tracker.isCompleted())
    }

    @Test
    fun `a null duration never completes and has no remaining time`() {
        val time = FakeTimeSource()
        val tracker = ElapsedTimeTracker(time)
        tracker.start(durationMillis = null)

        time.advanceBy(100_000)

        assertNull(tracker.remainingMillis())
        assertFalse(tracker.isCompleted())
    }

    @Test
    fun `pause freezes elapsed time`() {
        val time = FakeTimeSource()
        val tracker = ElapsedTimeTracker(time)
        tracker.start(durationMillis = 10_000)
        time.advanceBy(2_000)

        tracker.pause()
        time.advanceBy(3_000)

        assertEquals(2_000L, tracker.elapsedMillis())
    }

    @Test
    fun `resume continues accumulating from where it paused`() {
        val time = FakeTimeSource()
        val tracker = ElapsedTimeTracker(time)
        tracker.start(durationMillis = 10_000)
        time.advanceBy(2_000)
        tracker.pause()
        time.advanceBy(3_000) // while paused, must not count

        tracker.resume()
        time.advanceBy(1_000)

        assertEquals(3_000L, tracker.elapsedMillis())
    }

    @Test
    fun `start resets accumulated elapsed even if called again on a running tracker`() {
        val time = FakeTimeSource()
        val tracker = ElapsedTimeTracker(time)
        tracker.start(durationMillis = 10_000)
        time.advanceBy(4_000)

        tracker.start(durationMillis = 5_000)

        assertEquals(0L, tracker.elapsedMillis())
        assertEquals(5_000L, tracker.remainingMillis())
    }
}
