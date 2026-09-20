package com.pirxhio.affirmity.meditation

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveElapsedTimerTest {

    private class FakeTimeSource(private var current: Long = 1_000L) : MonotonicTimeSource {
        fun advanceBy(millis: Long) {
            current += millis
        }
        override fun nowMillis(): Long = current
    }

    private val time = FakeTimeSource()
    private val timer = ActiveElapsedTimer(time)

    @Test
    fun `elapsed is zero before start`() {
        time.advanceBy(5_000)
        assertEquals(0L, timer.elapsedMillis())
    }

    @Test
    fun `elapsed follows the clock while running`() {
        timer.start()
        time.advanceBy(7_000)
        assertEquals(7_000L, timer.elapsedMillis())
    }

    @Test
    fun `pause then resume excludes the paused gap`() {
        timer.start()
        time.advanceBy(10_000)
        timer.pause()
        time.advanceBy(60_000)
        timer.resume()
        time.advanceBy(5_000)
        assertEquals(15_000L, timer.elapsedMillis())
    }

    @Test
    fun `multiple pauses each exclude their own gap`() {
        timer.start()
        time.advanceBy(1_000)
        timer.pause()
        time.advanceBy(9_000)
        timer.resume()
        time.advanceBy(2_000)
        timer.pause()
        time.advanceBy(30_000)
        timer.resume()
        time.advanceBy(3_000)
        assertEquals(6_000L, timer.elapsedMillis())
    }

    @Test
    fun `ending while paused reports only the time before the pause`() {
        timer.start()
        time.advanceBy(4_000)
        timer.pause()
        time.advanceBy(120_000)
        assertEquals(4_000L, timer.elapsedMillis())
    }

    @Test
    fun `redundant pause and resume calls are ignored`() {
        timer.start()
        time.advanceBy(2_000)
        timer.pause()
        timer.pause()
        time.advanceBy(8_000)
        timer.resume()
        timer.resume()
        time.advanceBy(1_000)
        assertEquals(3_000L, timer.elapsedMillis())
    }

    @Test
    fun `pause before start does not start the timer`() {
        timer.pause()
        timer.resume()
        time.advanceBy(5_000)
        assertEquals(0L, timer.elapsedMillis())
    }
}
