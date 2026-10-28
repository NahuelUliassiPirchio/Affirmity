package com.pirxhio.affirmity.data

import kotlinx.coroutines.flow.Flow
import com.pirxhio.affirmity.data.repository.ActivityRepository
import com.pirxhio.affirmity.data.remote.runActivityRetrySession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ThrottleLatestTest {
    private val window = ACTIVITY_VIEW_SIGNAL_WINDOW_MILLIS

    /** Collects [source] throttled and returns the virtual timestamps of each emission. */
    private fun kotlinx.coroutines.test.TestScope.collectTimes(
        source: MutableSharedFlow<Unit>, into: MutableList<Long>,
    ) = backgroundScope.launch { source.throttleLatest(window).collect { into += testScheduler.currentTime } }

    @Test fun `window constant is ten seconds`() = assertEquals(10_000L, window)

    @Test fun `twenty rapid signals emit the leading one immediately and one trailing at window end`() = runTest {
        val source = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val times = mutableListOf<Long>()
        collectTimes(source, times)
        runCurrent()
        repeat(20) { source.emit(Unit); advanceTimeBy(100); runCurrent() }
        assertEquals(listOf(0L), times)
        advanceTimeBy(window); runCurrent()
        assertEquals(listOf(0L, window), times)
        advanceTimeBy(window * 5); runCurrent()
        assertEquals("no further emissions without new signals", 2, times.size)
    }

    @Test fun `a single signal emits once and no trailing duplicate`() = runTest {
        val source = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val times = mutableListOf<Long>()
        collectTimes(source, times)
        runCurrent()
        source.emit(Unit); runCurrent()
        advanceTimeBy(window * 3); runCurrent()
        assertEquals(listOf(0L), times)
    }

    @Test fun `signals spread further apart than the window each emit immediately`() = runTest {
        val source = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val times = mutableListOf<Long>()
        collectTimes(source, times)
        runCurrent()
        repeat(3) { source.emit(Unit); runCurrent(); advanceTimeBy(window + 1); runCurrent() }
        assertEquals(listOf(0L, window + 1, 2 * (window + 1)), times)
    }

    @Test fun `trailing signal is not postponed while signals keep arriving`() = runTest {
        val source = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val times = mutableListOf<Long>()
        collectTimes(source, times)
        runCurrent()
        repeat(30) { source.emit(Unit); runCurrent(); advanceTimeBy(1_000); runCurrent() }
        // continuous swiping for 30 s: leading at 0, then a trailing every window, never starved
        assertEquals(listOf(0L, 10_000L, 20_000L), times.take(3))
    }

    @Test fun `cancelling the collector drops the pending trailing signal`() = runTest {
        val source = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val times = mutableListOf<Long>()
        val job = collectTimes(source, times)
        runCurrent()
        repeat(5) { source.emit(Unit); runCurrent() }
        job.cancel(); runCurrent()
        advanceTimeBy(window * 2); runCurrent()
        assertEquals(listOf(0L), times)
        assertTrue("collector and its window timer are gone", job.isCancelled && job.isCompleted)
    }

    @Test fun `a signal arriving exactly at the window boundary is never lost`() = runTest {
        val times = mutableListOf<Long>()
        // The upstream's delay is registered before the throttle's window timer, so at t = window
        // the send runs first and the window timeout fires before the receiver resumes.
        val upstream = kotlinx.coroutines.flow.flow {
            emit(Unit); kotlinx.coroutines.delay(window); emit(Unit); kotlinx.coroutines.awaitCancellation()
        }
        backgroundScope.launch { upstream.throttleLatest(window).collect { times += testScheduler.currentTime } }
        runCurrent()
        advanceTimeBy(window * 5); runCurrent()
        assertEquals("leading plus the boundary signal: $times", 2, times.size)
        assertEquals(0L, times[0])
        assertTrue("surfaced within one window of the boundary: $times", times[1] in window..2 * window)
    }

    @Test fun `a slow downstream loses and duplicates nothing`() = runTest {
        val source = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val times = mutableListOf<Long>()
        backgroundScope.launch {
            source.throttleLatest(window).collect { times += testScheduler.currentTime; kotlinx.coroutines.delay(15_000) }
        }
        runCurrent()
        source.emit(Unit); runCurrent()                 // leading at 0, collector busy until 15 s
        repeat(5) { advanceTimeBy(1_000); source.emit(Unit); runCurrent() } // arrive while busy
        advanceTimeBy(window * 4); runCurrent()
        assertEquals("leading plus exactly one coalesced trailing: $times", listOf(0L, 25_000L), times)
    }

    @Test fun `trailing emission carries the latest value`() = runTest {
        val got = mutableListOf<Pair<Long, Int>>()
        val source = MutableSharedFlow<Int>(extraBufferCapacity = 64)
        backgroundScope.launch { source.throttleLatest(window).collect { got += testScheduler.currentTime to it } }
        runCurrent()
        (1..10).forEach { source.emit(it); runCurrent() }
        advanceTimeBy(window); runCurrent()
        assertEquals(listOf(0L to 1, window to 10), got)
    }

    @Test fun `a completed window wins over a continuously non-empty queue`() = runTest {
        val times = mutableListOf<Long>()
        // Downstream is busy for 15 s while the producer keeps the queue filled every millisecond,
        // so the timer is already done whenever the loop looks again.
        val upstream = kotlinx.coroutines.flow.flow { var i = 0; while (true) { emit(i++); kotlinx.coroutines.delay(1) } }
        backgroundScope.launch {
            upstream.throttleLatest(window).collect { times += testScheduler.currentTime; if (times.size == 1) kotlinx.coroutines.delay(15_000) }
        }
        runCurrent()
        advanceTimeBy(40_000); runCurrent()
        assertEquals("trailing emissions arrive at each window end: $times", listOf(0L, 25_000L, 35_000L), times.take(3))
    }

    @Test fun `upstream completion flushes the pending trailing signal`() = runTest {
        val times = mutableListOf<Long>()
        var completed = false
        backgroundScope.launch {
            kotlinx.coroutines.flow.flow { emit(Unit); emit(Unit); emit(Unit) }
                .throttleLatest(window).collect { times += testScheduler.currentTime }
            completed = true
        }
        runCurrent()
        advanceTimeBy(window * 2); runCurrent()
        assertEquals(2, times.size)
        assertTrue(completed)
    }

    @Test fun `upstream exception propagates`() = runTest {
        var caught: Throwable? = null
        backgroundScope.launch {
            try {
                kotlinx.coroutines.flow.flow { emit(Unit); throw IllegalStateException("boom") }
                    .throttleLatest(window).collect { }
            } catch (e: IllegalStateException) { caught = e }
        }
        runCurrent()
        assertEquals("boom", caught?.message)
    }

    // --- session-level behaviour: view signals throttled, completion and connectivity immediate ---

    private class CountingRepository : ActivityRepository {
        var passes = 0
        override suspend fun record(command: ActivityEvent): ActivitySaveState = error("Not used")
        override fun observeRange(owner: String, fromEpochDay: Long, toEpochDay: Long): Flow<ActivityRangeState> = error("Not used")
        override suspend fun rearmFailed(owner: String) = Unit
        override suspend fun retryPending(owner: String) { passes++ }
    }

    @Test fun `session runs two passes for twenty views, completion and connectivity stay immediate`() = runTest {
        val repo = CountingRepository()
        val views = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val completions = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val connectivity = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val job = backgroundScope.launch {
            runActivityRetrySession(repo, "account:A", { "account:A" },
                merge(connectivity, completions, views.throttleLatest(window)), onFailure = {})
        }
        runCurrent()
        val base = repo.passes
        repeat(20) { views.emit(Unit); advanceTimeBy(100); runCurrent() }
        assertEquals("leading view only", base + 1, repo.passes)
        completions.emit(Unit); runCurrent()
        assertEquals("completion is not delayed by the view window", base + 2, repo.passes)
        connectivity.emit(Unit); runCurrent()
        assertEquals("connectivity is unaffected", base + 3, repo.passes)
        advanceTimeBy(window); runCurrent()
        assertEquals("one trailing view pass", base + 4, repo.passes)
        job.cancel()
    }
}
