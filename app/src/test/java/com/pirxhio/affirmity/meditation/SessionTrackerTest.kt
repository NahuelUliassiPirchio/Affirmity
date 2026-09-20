package com.pirxhio.affirmity.meditation

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTrackerTest {

    private class FakeTime(var monotonic: Long = 5_000L, var wall: Long = 1_700_000_000_000L) : MonotonicTimeSource {
        override fun nowMillis(): Long = monotonic
        fun advance(millis: Long) {
            monotonic += millis
            wall += millis
        }
    }

    private val time = FakeTime()
    private val tracker = SessionTracker(monotonicTimeSource = time, wallClockMillis = { time.wall })

    @Test
    fun `summary before start is zero elapsed and stamps the current wall time`() {
        val summary = tracker.summary()
        assertEquals(SessionEndSummary(0L, 0L, time.wall), summary)
    }

    @Test
    fun `pause excludes the gap from active elapsed but not from wall elapsed`() {
        tracker.start()
        val startWall = time.wall
        time.advance(10_000)
        tracker.pause()
        time.advance(50_000)
        tracker.resume()
        time.advance(5_000)
        assertEquals(SessionEndSummary(wallElapsedSeconds = 65L, activeElapsedSeconds = 15L, startWallMillis = startWall), tracker.summary())
    }

    @Test
    fun `summary while paused freezes active elapsed and keeps wall elapsed growing`() {
        tracker.start()
        time.advance(4_000)
        tracker.pause()
        time.advance(120_000)
        val summary = tracker.summary()
        assertEquals(124L, summary.wallElapsedSeconds)
        assertEquals(4L, summary.activeElapsedSeconds)
    }

    @Test
    fun `multiple pauses each exclude their own gap`() {
        tracker.start()
        time.advance(1_000)
        tracker.pause()
        time.advance(9_000)
        tracker.resume()
        time.advance(2_000)
        tracker.pause()
        time.advance(30_000)
        tracker.resume()
        time.advance(3_000)
        val summary = tracker.summary()
        assertEquals(45L, summary.wallElapsedSeconds)
        assertEquals(6L, summary.activeElapsedSeconds)
    }

    @Test
    fun `pause and resume before start are ignored`() {
        tracker.pause()
        tracker.resume()
        time.advance(8_000)
        val summary = tracker.summary()
        assertEquals(0L, summary.wallElapsedSeconds)
        assertEquals(0L, summary.activeElapsedSeconds)
    }

    @Test
    fun `start stamps the wall start time once and restarting resets both clocks`() {
        tracker.start()
        time.advance(20_000)
        tracker.start()
        val restartWall = time.wall
        time.advance(3_000)
        assertEquals(SessionEndSummary(3L, 3L, restartWall), tracker.summary())
    }
}
