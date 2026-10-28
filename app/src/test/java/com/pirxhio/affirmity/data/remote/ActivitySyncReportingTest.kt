package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.pirxhio.affirmity.data.ActivityEvent
import com.pirxhio.affirmity.data.ActivityRangeState
import com.pirxhio.affirmity.data.ActivitySaveState
import com.pirxhio.affirmity.data.local.ActivityCoverageEntity
import com.pirxhio.affirmity.data.local.ActivityDao
import com.pirxhio.affirmity.data.local.ActivityEventEntity
import com.pirxhio.affirmity.data.repository.ActivityRepository
import com.pirxhio.affirmity.diagnostics.FakeSyncFailureReporter
import com.pirxhio.affirmity.diagnostics.OncePerRunSyncFailureReporter
import com.pirxhio.affirmity.diagnostics.SyncFailureKind
import com.pirxhio.affirmity.diagnostics.TerminalRowKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/**
 * Retry-loop policy under test: the loop reports one non-fatal per failure streak, at the 5th consecutive
 * failed pass when any failure in the streak is not network-transient, or at the 10th when every failure
 * is (UNAVAILABLE, DEADLINE_EXCEEDED, timeout). Only the loop's own passes are counted, not signal passes.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActivitySyncReportingTest {
    private val one = ActivityEventEntity("account:A", "evt-SECRET-1", 20_000, "guided", 1_000,
        0, 1_000, "UTC", "breathing", "Breathing", 120, "device", "PENDING")

    private class ScriptedRepository(private val outcomes: MutableList<suspend () -> Unit>) : ActivityRepository {
        var attempts = 0
        override suspend fun record(command: ActivityEvent): ActivitySaveState = error("Not used")
        override fun observeRange(owner: String, fromEpochDay: Long, toEpochDay: Long): Flow<ActivityRangeState> = error("Not used")
        override suspend fun rearmFailed(owner: String) = Unit
        override suspend fun retryPending(owner: String) {
            attempts++
            if (outcomes.isNotEmpty()) outcomes.removeAt(0)()
        }
    }

    private fun failing(times: Int, throwable: () -> Throwable = ::systematic): List<suspend () -> Unit> =
        List(times) { { throw throwable() } }
    private val ok: suspend () -> Unit = {}
    private fun systematic(): Throwable = IllegalStateException("users/uid-SECRET/x")
    private fun network(): Throwable = FirebaseFirestoreException("offline", Code.UNAVAILABLE)

    private fun repo(vararg steps: List<suspend () -> Unit>) =
        ScriptedRepository(steps.flatMap { it }.toMutableList())

    private fun TestScope.startLoop(
        repository: ActivityRepository,
        reporter: FakeSyncFailureReporter,
        owner: () -> String? = { "account:A" },
    ): Job = backgroundScope.launch {
        runActivityRetryLoop(repository, "account:A", owner, onFailure = {}, reporter = reporter)
    }

    private fun TestScope.runFor(millis: Long) { advanceTimeBy(millis); runCurrent() }

    @Test fun `five consecutive non-network failures report one non-fatal with class and code`() = runTest {
        val fake = FakeSyncFailureReporter()
        val repository = repo(failing(5) { FirebaseFirestoreException("PERMISSION_DENIED: users/uid-SECRET", Code.ABORTED) })
        startLoop(repository, fake)
        runCurrent()
        runFor(14_999)
        assertEquals(4, repository.attempts)
        assertTrue(fake.reports.isEmpty())
        runFor(1)
        val report = fake.reports.single()
        assertEquals(SyncFailureKind.RETRY_LOOP, report.kind)
        assertEquals("ABORTED", report.firestoreCode)
        assertEquals("com.google.firebase.firestore.FirebaseFirestoreException", report.errorClass)
        assertTrue(!report.toString().contains("SECRET"))
    }

    @Test fun `three failures, a success, then three failures never report because the streak resets`() = runTest {
        val fake = FakeSyncFailureReporter()
        val repository = repo(failing(3), listOf(ok), failing(3))
        startLoop(repository, fake)
        runCurrent()
        runFor(400_000)
        assertTrue(repository.attempts >= 8)
        assertTrue(fake.reports.isEmpty())
    }

    @Test fun `five failures, a success, then five failures report twice at loop level`() = runTest {
        val fake = FakeSyncFailureReporter()
        val repository = repo(failing(5), listOf(ok), failing(5))
        startLoop(repository, fake)
        runCurrent()
        runFor(600_000)
        assertTrue(repository.attempts >= 11)
        assertEquals(2, fake.reports.size)
    }

    @Test fun `sixth and later failures in one streak do not report again`() = runTest {
        val fake = FakeSyncFailureReporter()
        val repository = repo(failing(9))
        startLoop(repository, fake)
        runCurrent()
        runFor(600_000)
        assertTrue(repository.attempts >= 9)
        assertEquals(1, fake.reports.size)
    }

    @Test fun `a streak of only network failures reports at the tenth, not the fifth`() = runTest {
        val fake = FakeSyncFailureReporter()
        val repository = repo(failing(10, ::network))
        startLoop(repository, fake)
        runCurrent()
        runFor(15_000)
        assertEquals(5, repository.attempts)
        assertTrue(fake.reports.isEmpty())
        runFor(600_000)
        val report = fake.reports.single()
        assertEquals("UNAVAILABLE", report.firestoreCode)
    }

    @Test fun `deadline exceeded counts as network-transient`() = runTest {
        val fake = FakeSyncFailureReporter()
        startLoop(repo(failing(9) { FirebaseFirestoreException("slow", Code.DEADLINE_EXCEEDED) }), fake)
        runCurrent()
        runFor(600_000)
        assertTrue(fake.reports.isEmpty())
    }

    @Test fun `a streak of only timeouts is network-transient`() = runTest {
        val fake = FakeSyncFailureReporter()
        val timeouts = List<suspend () -> Unit>(9) { { withTimeout(1) { delay(10) } } }
        val repository = repo(timeouts)
        startLoop(repository, fake)
        runCurrent()
        runFor(600_000)
        assertTrue(repository.attempts >= 9)
        assertTrue(fake.reports.isEmpty())
    }

    @Test fun `a non-network failure in a network streak reports at the fifth and names the non-network error`() = runTest {
        val fake = FakeSyncFailureReporter()
        val repository = repo(failing(4, ::network), failing(1))
        startLoop(repository, fake)
        runCurrent()
        runFor(15_000)
        val report = fake.reports.single()
        assertEquals("java.lang.IllegalStateException", report.errorClass)
    }

    @Test fun `cancelling in the middle of a streak reports nothing`() = runTest {
        val fake = FakeSyncFailureReporter()
        val job = startLoop(repo(failing(10)), fake)
        runCurrent()
        runFor(3_000)
        job.cancel()
        runFor(600_000)
        assertTrue(job.isCancelled)
        assertTrue(fake.reports.isEmpty())
    }

    @Test fun `an owner change in the middle of a streak reports nothing`() = runTest {
        val fake = FakeSyncFailureReporter()
        var owner = "account:A"
        val repository = repo(failing(10))
        val job = startLoop(repository, fake) { owner }
        runCurrent()
        runFor(3_000)
        owner = "account:B"
        runFor(600_000)
        assertTrue(job.isCompleted)
        assertTrue(fake.reports.isEmpty())
    }

    @Test fun `once-per-run wrapper keeps a second identical streak silent`() = runTest {
        val fake = FakeSyncFailureReporter()
        val repository = repo(failing(5), listOf(ok), failing(5))
        backgroundScope.launch {
            runActivityRetryLoop(repository, "account:A", { "account:A" }, onFailure = {},
                reporter = OncePerRunSyncFailureReporter(fake))
        }
        runCurrent()
        runFor(600_000)
        assertEquals(1, fake.reports.size)
    }

    @Test fun `a throwing reporter does not stop the retry loop`() = runTest {
        val throwing = FakeSyncFailureReporter(IllegalStateException("sdk down"))
        val repository = repo(failing(5), listOf(ok))
        startLoop(repository, throwing)
        runCurrent()
        runFor(15_000)
        assertEquals(1, throwing.reports.size)
        runFor(120_000)
        assertTrue("loop kept retrying after the reporter threw", repository.attempts >= 7)
    }

    @Test fun `session loop reports through the same reporter`() = runTest {
        val fake = FakeSyncFailureReporter()
        val repository = repo(failing(5))
        backgroundScope.launch {
            runActivityRetrySession(repository, "account:A", { "account:A" }, flowOf(), onFailure = {}, reporter = fake)
        }
        runCurrent()
        runFor(15_000)
        assertEquals(1, fake.reports.size)
    }

    private class Journal(rows: List<ActivityEventEntity>) {
        val dao: ActivityDao = Mockito.mock(ActivityDao::class.java).also { dao -> runBlocking {
            val states = rows.associate { it.eventId to it.uploadState }.toMutableMap()
            Mockito.`when`(dao.pending(Mockito.anyString(), Mockito.anyInt())).thenAnswer {
                rows.filter { states[it.eventId] == "PENDING" }
            }
            Mockito.doAnswer { states[it.getArgument<String>(1)] = "FAILED"; null }
                .`when`(dao).markFailed(Mockito.anyString(), Mockito.anyString())
        } }
    }

    @Test fun `terminal FAILED rows are reported once per code with kind and no identifiers`() = runTest {
        val fake = FakeSyncFailureReporter()
        val rows = listOf(one, one.copy(eventId = "evt-SECRET-2"), one.copy(eventId = "evt-SECRET-3"))
        val sync = ActivityEventSync(Journal(rows).dao, "A", ActivityUploader { _, event ->
            throw FirebaseFirestoreException("users/uid-SECRET/${event.eventId}",
                if (event.eventId.endsWith("3")) Code.INVALID_ARGUMENT else Code.PERMISSION_DENIED)
        }, currentUid = { "A" }, reporter = OncePerRunSyncFailureReporter(fake))
        sync.retryPending()
        assertEquals(listOf("PERMISSION_DENIED", "INVALID_ARGUMENT"), fake.reports.map { it.firestoreCode })
        assertTrue(fake.reports.all { it.kind == SyncFailureKind.TERMINAL_UPLOAD && it.rowKind == TerminalRowKind.EVENT })
        assertTrue(fake.reports.none { it.toString().contains("SECRET") })
    }

    @Test fun `a throwing reporter does not stop the terminal pass`() = runTest {
        val rows = listOf(one, one.copy(eventId = "evt-SECRET-2"))
        val uploaded = mutableListOf<String>()
        val sync = ActivityEventSync(Journal(rows).dao, "A", ActivityUploader { _, event ->
            if (event.eventId.endsWith("1")) throw FirebaseFirestoreException("x", Code.PERMISSION_DENIED)
            uploaded += event.eventId
        }, currentUid = { "A" }, reporter = FakeSyncFailureReporter(IllegalStateException("sdk down")))
        sync.retryPending()
        assertEquals(listOf("evt-SECRET-2"), uploaded)
    }

    @Test fun `transient upload failures are not reported as terminal`() = runTest {
        val fake = FakeSyncFailureReporter()
        val sync = ActivityEventSync(Journal(listOf(one)).dao, "A", ActivityUploader { _, _ ->
            throw FirebaseFirestoreException("offline", Code.UNAVAILABLE)
        }, currentUid = { "A" }, reporter = fake)
        runCatching { sync.retryPending() }
        assertTrue(fake.reports.isEmpty())
    }

    @Test fun `terminal coverage rows are reported with coverage kind and no identifiers`() = runTest {
        val fake = FakeSyncFailureReporter()
        val coverage = ActivityCoverageEntity("account:A", "src-SECRET", 20_000, 20_001, 1, false, "PENDING")
        val dao = Mockito.mock(ActivityDao::class.java)
        runBlocking {
            Mockito.`when`(dao.pending(Mockito.anyString(), Mockito.anyInt())).thenReturn(emptyList())
            var served = false
            Mockito.`when`(dao.pendingCoverage(Mockito.anyString(), Mockito.anyInt())).thenAnswer {
                if (served) emptyList() else listOf(coverage).also { served = true }
            }
        }
        val remote = object : ActivityRemoteClient {
            override suspend fun uploadEvent(uid: String, event: ActivityEventEntity) = Unit
            override suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity) {
                throw FirebaseFirestoreException("users/uid-SECRET", Code.FAILED_PRECONDITION)
            }
            override fun observeEvents(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityEventEntity>())
            override fun observeCoverage(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityCoverageEntity>())
        }
        FirestoreActivityRepository(dao, "A", remote, currentUid = { "A" }, reporter = fake).retryPending("account:A")
        val report = fake.reports.single()
        assertEquals(TerminalRowKind.COVERAGE, report.rowKind)
        assertEquals("FAILED_PRECONDITION", report.firestoreCode)
        assertTrue(!report.toString().contains("SECRET"))
    }
}
