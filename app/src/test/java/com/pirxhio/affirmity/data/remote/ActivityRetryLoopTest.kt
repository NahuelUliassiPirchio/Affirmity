package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.ActivityEvent
import com.pirxhio.affirmity.data.ActivityRangeState
import com.pirxhio.affirmity.data.ActivitySaveState
import com.pirxhio.affirmity.data.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActivityRetryLoopTest {
    @Test fun `connectivity signal retries immediately and account change cannot redirect it`() = runTest {
        var owner = "account:A"
        val signals = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
        var attempts = 0
        val repository = RetryRepository { attempts++ }
        val job = backgroundScope.launch {
            runActivityRetrySession(repository, "account:A", { owner }, signals) {}
        }
        runCurrent()
        assertEquals(1, attempts)
        signals.emit(Unit)
        runCurrent()
        assertEquals(2, attempts)
        owner = "account:B"
        signals.emit(Unit)
        runCurrent()
        assertEquals(2, attempts)
        job.cancel()
    }

    @Test fun `startup retries offline payload with backoff then clears error on success`() = runTest {
        var owner = "account:A"
        val attempts = mutableListOf<Long>()
        val failures = mutableListOf<String?>()
        val repository = RetryRepository {
            attempts += testScheduler.currentTime
            if (attempts.size == 1) error("Offline")
        }
        backgroundScope.launch { runActivityRetryLoop(repository, "account:A", { owner }) { failures += it?.message } }
        runCurrent()
        advanceTimeBy(999)
        runCurrent()
        assertEquals(listOf(0L), attempts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(0L, 1_000L), attempts)
        assertEquals(listOf("Offline", null), failures)
        owner = "account:B"
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(listOf(0L, 1_000L), attempts)
    }
    @Test fun `repeated failures increase backoff rather than spin`() = runTest {
        val attempts = mutableListOf<Long>()
        val repository = RetryRepository { attempts += testScheduler.currentTime; error("Offline") }
        backgroundScope.launch { runActivityRetryLoop(repository, "account:A", { "account:A" }) {} }
        runCurrent()
        advanceTimeBy(7_000)
        runCurrent()
        assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L), attempts)
    }

    private class RetryRepository(private val retry: suspend () -> Unit) : ActivityRepository {
        override suspend fun record(command: ActivityEvent): ActivitySaveState = error("Not used")
        override fun observeRange(owner: String, fromEpochDay: Long, toEpochDay: Long): Flow<ActivityRangeState> = error("Not used")
        override suspend fun retryPending(owner: String) = retry()
    }
}
