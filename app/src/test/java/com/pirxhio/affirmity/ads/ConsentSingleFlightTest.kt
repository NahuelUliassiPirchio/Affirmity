package com.pirxhio.affirmity.ads

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentSingleFlightTest {

    private val completed = ConsentGatherResult.Completed
    private val failed = ConsentGatherResult.Failed("boom")

    @Test
    fun `concurrent callers share one request`() = runTest {
        val flight = ConsentSingleFlight(backgroundScope)
        var starts = 0
        val gate = CompletableDeferred<ConsentGatherResult>()
        val request: suspend () -> ConsentGatherResult = { starts++; gate.await() }

        val a = async { flight.run(request) }
        val b = async { flight.run(request) }
        val c = async { flight.runOnceAfterSuccess(request) }
        runCurrent()
        gate.complete(completed)

        assertEquals(completed, a.await())
        assertEquals(completed, b.await())
        assertEquals(completed, c.await())
        assertEquals(1, starts)
    }

    @Test
    fun `cancelling a waiting caller does not cancel the request`() = runTest {
        val flight = ConsentSingleFlight(backgroundScope)
        var starts = 0
        val gate = CompletableDeferred<ConsentGatherResult>()
        val request: suspend () -> ConsentGatherResult = { starts++; gate.await() }

        val first = async { flight.run(request) }
        runCurrent()
        first.cancel() // e.g. Activity recreated, LaunchedEffect cancelled
        runCurrent()

        val second = async { flight.runOnceAfterSuccess(request) }
        runCurrent()
        gate.complete(completed)

        assertEquals(completed, second.await())
        assertEquals(1, starts)
    }

    @Test
    fun `after success the launch path does not run again`() = runTest {
        val flight = ConsentSingleFlight(backgroundScope)
        var starts = 0
        val request: suspend () -> ConsentGatherResult = { starts++; completed }

        assertEquals(completed, flight.runOnceAfterSuccess(request))
        assertNull(flight.runOnceAfterSuccess(request))
        assertEquals(1, starts)
    }

    @Test
    fun `after success the explicit path still runs a fresh request`() = runTest {
        val flight = ConsentSingleFlight(backgroundScope)
        var starts = 0
        val request: suspend () -> ConsentGatherResult = { starts++; completed }

        flight.run(request)
        flight.run(request)
        assertEquals(2, starts)
    }

    @Test
    fun `after a failure a later call starts a fresh request`() = runTest {
        val flight = ConsentSingleFlight(backgroundScope)
        var starts = 0
        val results = ArrayDeque(listOf(failed, completed))
        val request: suspend () -> ConsentGatherResult = { starts++; results.removeFirst() }

        assertEquals(failed, flight.runOnceAfterSuccess(request))
        assertEquals(completed, flight.runOnceAfterSuccess(request))
        assertEquals(2, starts)
    }

    @Test
    fun `a request that never completes fails after the timeout and frees the slot`() = runTest {
        val flight = ConsentSingleFlight(backgroundScope, timeoutMs = 1_000L)
        var starts = 0
        val hang: suspend () -> ConsentGatherResult = { starts++; awaitCancellation() }

        val result = flight.run(hang)

        assertTrue(result is ConsentGatherResult.Failed)
        assertEquals(1_000L, testScheduler.currentTime)
        // Slot is free and a timeout is not success: the launch path may run again.
        assertEquals(completed, flight.runOnceAfterSuccess { starts++; completed })
        assertEquals(2, starts)
    }

    @Test
    fun `after a timeout a later call starts a fresh request that can succeed`() = runTest {
        val flight = ConsentSingleFlight(backgroundScope, timeoutMs = 1_000L)
        var starts = 0

        assertTrue(flight.run { starts++; awaitCancellation() } is ConsentGatherResult.Failed)
        assertEquals(completed, flight.run { starts++; completed })
        assertEquals(2, starts)
    }

    @Test
    fun `joiners present during a hang also receive Failed`() = runTest {
        val flight = ConsentSingleFlight(backgroundScope, timeoutMs = 1_000L)
        var starts = 0
        val hang: suspend () -> ConsentGatherResult = { starts++; awaitCancellation() }

        val a = async { flight.run(hang) }
        val b = async { flight.run(hang) }
        val c = async { flight.runOnceAfterSuccess(hang) }

        assertTrue(a.await() is ConsentGatherResult.Failed)
        assertTrue(b.await() is ConsentGatherResult.Failed)
        assertTrue(c.await() is ConsentGatherResult.Failed)
        assertEquals(1, starts)
    }

    @Test
    fun `a request that completes before the timeout is unaffected`() = runTest {
        val flight = ConsentSingleFlight(backgroundScope, timeoutMs = 1_000L)
        var starts = 0

        val result = flight.runOnceAfterSuccess {
            starts++
            delay(500L)
            completed
        }

        assertEquals(completed, result)
        assertNull(flight.runOnceAfterSuccess { starts++; completed })
        assertEquals(1, starts)
    }

    @Test
    fun `a throwing request propagates and releases the slot`() = runTest {
        // Same shape as production: a supervised scope, so the failure stays with the awaiters.
        val flight = ConsentSingleFlight(CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        val boom: suspend () -> ConsentGatherResult = { throw IllegalStateException("sdk") }

        val thrown = runCatching { flight.run(boom) }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertEquals(completed, flight.run { completed })
        advanceUntilIdle()
    }
}
