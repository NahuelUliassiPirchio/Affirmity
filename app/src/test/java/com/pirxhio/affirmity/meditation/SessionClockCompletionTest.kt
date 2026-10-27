package com.pirxhio.affirmity.meditation

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionClockCompletionTest {
    @Test fun `completion captures exact wall and zone before subscriber delay`() = runTest {
        var now = 0L
        var wall = 1_000L
        var zone = "UTC"
        val clock = RealSessionClock(backgroundScope, MonotonicTimeSource { now }, wallClockMillis = { wall }, zoneId = { zone })
        val events = mutableListOf<ClockEvent.Completed>()
        backgroundScope.launch { clock.events.collect { if (it is ClockEvent.Completed) events += it } }
        runCurrent()
        clock.start(1_000)
        runCurrent()
        now = 1_200
        wall = 2_200
        advanceTimeBy(200)
        runCurrent()
        wall = 99_000
        zone = "Asia/Tokyo"
        assertEquals(2_200L, events.single().endWallMillis)
        assertEquals("UTC", events.single().endZoneId)
    }

    @Test
    fun `completion excludes paused time`() = runTest {
        var now = 0L
        val clock = RealSessionClock(backgroundScope, MonotonicTimeSource { now })
        val completions = mutableListOf<ClockEvent.Completed>()
        backgroundScope.launch { clock.events.collect { if (it is ClockEvent.Completed) completions += it } }
        runCurrent()
        clock.start(1_000)
        runCurrent()
        now = 400
        clock.pause()
        now = 60_400
        clock.resume()
        runCurrent()
        now = 61_100
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf(1_100L), completions.map { it.activeElapsedMillis })
    }

    @Test
    fun `stopping early never emits a completion`() = runTest {
        var now = 0L
        val clock = RealSessionClock(backgroundScope, MonotonicTimeSource { now })
        val completions = mutableListOf<ClockEvent.Completed>()
        backgroundScope.launch { clock.events.collect { if (it is ClockEvent.Completed) completions += it } }
        runCurrent()
        clock.start(1_000)
        runCurrent()
        now = 400
        clock.stop()
        now = 20_000
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(emptyList<ClockEvent.Completed>(), completions)
    }

    @Test
    fun `completion freezes actual elapsed when tick arrives late`() = runTest {
        var now = 0L
        val clock = RealSessionClock(backgroundScope, MonotonicTimeSource { now })
        val completions = mutableListOf<ClockEvent.Completed>()
        backgroundScope.launch { clock.events.collect { if (it is ClockEvent.Completed) completions += it } }
        runCurrent()
        clock.start(1_000)
        runCurrent()
        now = 1_750
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf(1_750L), completions.map { it.activeElapsedMillis })
        clock.start(5_000)
        now = 9_000
        assertEquals(1_750L, completions.single().activeElapsedMillis)
        clock.stop()
    }
}
