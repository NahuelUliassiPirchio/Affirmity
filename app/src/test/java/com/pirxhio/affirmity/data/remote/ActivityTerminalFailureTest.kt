package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.pirxhio.affirmity.data.local.ActivityCoverageEntity
import com.pirxhio.affirmity.data.local.ActivityDao
import com.pirxhio.affirmity.data.local.ActivityEventEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import com.pirxhio.affirmity.data.repository.ActivityRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActivityTerminalFailureTest {
    private val one = ActivityEventEntity("account:A", "one", 20_000, "guided", 1_000,
        0, 1_000, "UTC", "breathing", "Breathing", 120, "device", "PENDING")
    private val two = one.copy(eventId = "two")

    private fun firestore(code: Code) = FirebaseFirestoreException("boom", code)

    /** Stateful DAO double: pending() reflects PENDING rows only, like the SQL query. */
    private class Journal(rows: List<ActivityEventEntity>) {
        val states = rows.associate { it.eventId to it.uploadState }.toMutableMap()
        val rows = rows
        val dao: ActivityDao = Mockito.mock(ActivityDao::class.java).also { dao -> runBlocking {
            Mockito.`when`(dao.pending("account:A")).thenAnswer {
                rows.filter { states[it.eventId] == "PENDING" }
            }
            Mockito.doAnswer { states[it.getArgument<String>(1)] = "UPLOADED"; null }
                .`when`(dao).acknowledge(Mockito.anyString(), Mockito.anyString())
            Mockito.doAnswer { states[it.getArgument<String>(1)] = "FAILED"; null }
                .`when`(dao).markFailed(Mockito.anyString(), Mockito.anyString())
        } }
    }

    @Test fun `terminal failure on oldest event marks it FAILED and later events still upload`() = runTest {
        val journal = Journal(listOf(one, two))
        val uploaded = mutableListOf<String>()
        val sync = ActivityEventSync(journal.dao, "A", ActivityUploader { _, event ->
            if (event.eventId == "one") throw firestore(Code.PERMISSION_DENIED)
            uploaded += event.eventId
        }) { "A" }
        sync.retryPending()
        assertEquals(mapOf("one" to "FAILED", "two" to "UPLOADED"), journal.states)
        assertEquals(listOf("two"), uploaded)
        Mockito.verify(journal.dao, Mockito.never()).acknowledge("account:A", "one")
    }

    @Test fun `failed row is not re-sent on later passes`() = runTest {
        val journal = Journal(listOf(one))
        var attempts = 0
        val sync = ActivityEventSync(journal.dao, "A", ActivityUploader { _, _ ->
            attempts++
            throw firestore(Code.INVALID_ARGUMENT)
        }) { "A" }
        sync.retryPending()
        sync.retryPending()
        assertEquals(1, attempts)
    }

    @Test fun `local payload validation failure is terminal`() = runTest {
        val journal = Journal(listOf(one, two))
        val sync = ActivityEventSync(journal.dao, "A", ActivityUploader { _, event ->
            if (event.eventId == "one") throw IllegalArgumentException("bad payload")
        }) { "A" }
        sync.retryPending()
        assertEquals(mapOf("one" to "FAILED", "two" to "UPLOADED"), journal.states)
    }

    @Test fun `transient failure aborts the pass and leaves the row pending`() = runTest {
        val journal = Journal(listOf(one, two))
        val seen = mutableListOf<String>()
        val sync = ActivityEventSync(journal.dao, "A", ActivityUploader { _, event ->
            seen += event.eventId
            throw firestore(Code.UNAVAILABLE)
        }) { "A" }
        try {
            sync.retryPending()
            fail("transient failures must propagate for backoff")
        } catch (failure: FirebaseFirestoreException) {
            assertEquals(Code.UNAVAILABLE, failure.code)
        }
        assertEquals(listOf("one"), seen)
        assertEquals(mapOf("one" to "PENDING", "two" to "PENDING"), journal.states)
    }

    @Test fun `timeout is transient and leaves the row pending`() = runTest {
        val journal = Journal(listOf(one))
        val sync = ActivityEventSync(journal.dao, "A", ActivityUploader { _, _ ->
            kotlinx.coroutines.delay(60_000)
        }) { "A" }
        try {
            sync.retryPending()
            fail("timeout must propagate")
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            assertEquals(mapOf("one" to "PENDING"), journal.states)
        }
    }

    @Test fun `terminal coverage failure is marked FAILED and later coverage still uploads`() = runTest {
        val first = ActivityCoverageEntity("account:A", "device", 20_000, 20_001, 1, false, "PENDING")
        val second = first.copy(fromEpochDay = 20_002, toEpochDay = 20_003)
        val states = mutableMapOf(first to "PENDING", second to "PENDING")
        val dao = Mockito.mock(ActivityDao::class.java)
        Mockito.`when`(dao.pending("account:A")).thenReturn(emptyList())
        Mockito.`when`(dao.pendingCoverage("account:A")).thenAnswer {
            states.filterValues { it == "PENDING" }.keys.toList()
        }
        Mockito.doAnswer { states[second] = "UPLOADED"; null }.`when`(dao).acknowledgeCoverage(second)
        Mockito.doAnswer { states[first] = "FAILED"; null }.`when`(dao).markCoverageFailed(first)
        val uploaded = mutableListOf<Long>()
        val remote = object : ActivityRemoteClient {
            override suspend fun uploadEvent(uid: String, event: ActivityEventEntity) = Unit
            override suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity) {
                if (coverage == first) throw firestore(Code.FAILED_PRECONDITION)
                uploaded += coverage.fromEpochDay
            }
            override fun observeEvents(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityEventEntity>())
            override fun observeCoverage(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityCoverageEntity>())
        }
        FirestoreActivityRepository(dao, "A", remote) { "A" }.retryPending("account:A")
        assertEquals(mapOf(first to "FAILED", second to "UPLOADED"), states)
        assertEquals(listOf(20_002L), uploaded)
    }

    @Test fun `transient coverage failure propagates and keeps coverage pending`() = runTest {
        val first = ActivityCoverageEntity("account:A", "device", 20_000, 20_001, 1, false, "PENDING")
        val dao = Mockito.mock(ActivityDao::class.java)
        Mockito.`when`(dao.pending("account:A")).thenReturn(emptyList())
        Mockito.`when`(dao.pendingCoverage("account:A")).thenReturn(listOf(first))
        val remote = object : ActivityRemoteClient {
            override suspend fun uploadEvent(uid: String, event: ActivityEventEntity) = Unit
            override suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity) =
                throw firestore(Code.DEADLINE_EXCEEDED)
            override fun observeEvents(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityEventEntity>())
            override fun observeCoverage(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityCoverageEntity>())
        }
        try {
            FirestoreActivityRepository(dao, "A", remote) { "A" }.retryPending("account:A")
            fail("transient failures must propagate")
        } catch (_: FirebaseFirestoreException) {
            Mockito.verify(dao, Mockito.never()).markCoverageFailed(first)
        }
    }

    @Test fun `real cancellation during upload propagates and leaves the row pending`() = runTest {
        val journal = Journal(listOf(one))
        val started = CompletableDeferred<Unit>()
        val sync = ActivityEventSync(journal.dao, "A", ActivityUploader { _, _ ->
            started.complete(Unit)
            awaitCancellation()
        }) { "A" }
        val job = launch { sync.retryPending() }
        started.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(mapOf("one" to "PENDING"), journal.states)
    }

    @Test fun `coverage timeout is transient and keeps coverage pending`() = runTest {
        val first = ActivityCoverageEntity("account:A", "device", 20_000, 20_001, 1, false, "PENDING")
        val dao = Mockito.mock(ActivityDao::class.java)
        Mockito.`when`(dao.pending("account:A")).thenReturn(emptyList())
        Mockito.`when`(dao.pendingCoverage("account:A")).thenReturn(listOf(first))
        val remote = object : ActivityRemoteClient {
            override suspend fun uploadEvent(uid: String, event: ActivityEventEntity) = Unit
            override suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity) = delay(60_000)
            override fun observeEvents(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityEventEntity>())
            override fun observeCoverage(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityCoverageEntity>())
        }
        try {
            FirestoreActivityRepository(dao, "A", remote) { "A" }.retryPending("account:A")
            fail("timeout must propagate")
        } catch (_: TimeoutCancellationException) {
            Mockito.verify(dao, Mockito.never()).markCoverageFailed(first)
        }
    }

    @Test fun `re-arm runs once per session and a previously failed row uploads in its first pass`() = runTest {
        val states = mutableMapOf("one" to "FAILED")
        val dao = Mockito.mock(ActivityDao::class.java)
        Mockito.`when`(dao.pending("account:A")).thenAnswer { listOf(one).filter { states[it.eventId] == "PENDING" } }
        Mockito.`when`(dao.pendingCoverage("account:A")).thenReturn(emptyList())
        Mockito.doAnswer { states.replaceAll { _, _ -> "PENDING" }; null }.`when`(dao).rearmFailed("account:A")
        Mockito.doAnswer { states[it.getArgument(1)] = "UPLOADED"; null }
            .`when`(dao).acknowledge(Mockito.anyString(), Mockito.anyString())
        val uploaded = mutableListOf<String>()
        val remote = object : ActivityRemoteClient {
            override suspend fun uploadEvent(uid: String, event: ActivityEventEntity) { uploaded += event.eventId }
            override suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity) = Unit
            override fun observeEvents(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityEventEntity>())
            override fun observeCoverage(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityCoverageEntity>())
        }
        val repo = FirestoreActivityRepository(dao, "A", remote) { "A" }
        val signals = MutableSharedFlow<Unit>()
        val session = backgroundScope.launch {
            runActivityRetrySession(repo, "account:A", { "account:A" }, signals) { }
        }
        runCurrent()
        signals.emit(Unit)
        runCurrent()
        assertEquals(listOf("one"), uploaded)
        Mockito.verify(dao, Mockito.times(1)).rearmFailed("account:A")
        Mockito.verify(dao, Mockito.times(1)).rearmFailedCoverage("account:A")
        session.cancel()
    }

    @Test fun `failing re-arm is reported and does not stop the session`() = runTest {
        var retries = 0
        val failures = mutableListOf<String?>()
        val repo = object : ActivityRepository {
            override suspend fun record(command: com.pirxhio.affirmity.data.ActivityEvent) = error("Not used")
            override fun observeRange(owner: String, fromEpochDay: Long, toEpochDay: Long) = error("Not used")
            override suspend fun rearmFailed(owner: String) = throw IllegalStateException("disk")
            override suspend fun retryPending(owner: String) { retries++ }
        }
        val signals = MutableSharedFlow<Unit>()
        val session = backgroundScope.launch {
            runActivityRetrySession(repo, "account:A", { "account:A" }, signals) { failures += it?.javaClass?.simpleName }
        }
        runCurrent()
        signals.emit(Unit)
        runCurrent()
        assertTrue(retries >= 2)
        assertEquals("IllegalStateException", failures.first())
        session.cancel()
    }

    @Test fun `record reports FAILED when re-recording an event whose row is FAILED`() = runTest {
        val dao = Mockito.mock(ActivityDao::class.java)
        val command = com.pirxhio.affirmity.data.ActivityEvent("account:A", "one", 20_000,
            com.pirxhio.affirmity.data.ActivityKind.GUIDED, 1_000, 0, 1_000, "UTC", "breathing", "Breathing", 120, "device")
        Mockito.`when`(dao.record(one)).thenReturn(one.copy(uploadState = "FAILED"))
        val repo = FirestoreActivityRepository(dao, "A", object : ActivityRemoteClient {
            override suspend fun uploadEvent(uid: String, event: ActivityEventEntity) = Unit
            override suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity) = Unit
            override fun observeEvents(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityEventEntity>())
            override fun observeCoverage(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityCoverageEntity>())
        }) { "A" }
        assertEquals(com.pirxhio.affirmity.data.ActivitySaveState.FAILED, repo.record(command))
    }

    @Test fun `re-arm is skipped when the session owner is no longer current at start`() = runTest {
        var rearms = 0
        val repo = object : ActivityRepository {
            override suspend fun record(command: com.pirxhio.affirmity.data.ActivityEvent) = error("Not used")
            override fun observeRange(owner: String, fromEpochDay: Long, toEpochDay: Long) = error("Not used")
            override suspend fun rearmFailed(owner: String) { rearms++ }
            override suspend fun retryPending(owner: String) = Unit
        }
        val session = backgroundScope.launch {
            runActivityRetrySession(repo, "account:A", { "account:B" }, MutableSharedFlow()) { }
        }
        runCurrent()
        assertEquals(0, rearms)
        session.cancel()
    }

    @Test fun `classifier treats permanent Firestore rejections as terminal`() {
        for (code in listOf(Code.PERMISSION_DENIED, Code.INVALID_ARGUMENT, Code.FAILED_PRECONDITION)) {
            assertTrue("$code", isTerminalUploadFailure(firestore(code)))
        }
    }

    @Test fun `classifier treats connectivity and unknown failures as transient`() {
        for (code in listOf(Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.UNKNOWN, Code.UNAUTHENTICATED,
            Code.RESOURCE_EXHAUSTED, Code.ABORTED, Code.CANCELLED, Code.INTERNAL)) {
            assertFalse("$code", isTerminalUploadFailure(firestore(code)))
        }
        assertFalse(isTerminalUploadFailure(IllegalStateException("Offline")))
        assertFalse(isTerminalUploadFailure(java.io.IOException("reset")))
        assertFalse(isTerminalUploadFailure(CancellationException("cancelled")))
    }

    @Test fun `classifier treats local payload validation failure as terminal`() {
        assertTrue(isTerminalUploadFailure(IllegalArgumentException("bad payload")))
    }
}
