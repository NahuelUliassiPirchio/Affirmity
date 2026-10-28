package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.pirxhio.affirmity.data.local.ACTIVITY_PENDING_PAGE_SIZE
import com.pirxhio.affirmity.data.local.ActivityCoverageEntity
import com.pirxhio.affirmity.data.local.ActivityDao
import com.pirxhio.affirmity.data.local.ActivityEventEntity
import com.pirxhio.affirmity.diagnostics.NoOpSyncFailureReporter
import com.pirxhio.affirmity.diagnostics.OncePerRunSyncFailureReporter
import com.pirxhio.affirmity.diagnostics.SyncFailureKind
import com.pirxhio.affirmity.diagnostics.SyncFailureReport
import com.pirxhio.affirmity.diagnostics.SyncFailureReporter
import com.pirxhio.affirmity.diagnostics.TerminalRowKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito

class ActivityPendingPagingTest {
    private val template = ActivityEventEntity("account:A", "e000", 20_000, "guided", 1_000,
        0, 1_000, "UTC", "breathing", "Breathing", 120, "device", "PENDING")

    private fun events(count: Int) = (0 until count).map { template.copy(eventId = "e%03d".format(it)) }

    private fun firestore(code: Code) = FirebaseFirestoreException("boom", code)

    /** Stateful DAO double honouring the page limit like the SQL LIMIT does. */
    private class Journal(val rows: List<ActivityEventEntity>, acknowledges: Boolean = true) {
        val states = rows.associate { it.eventId to "PENDING" }.toMutableMap()
        val requestedLimits = mutableListOf<Int>()
        val pageSizes = mutableListOf<Int>()
        val dao: ActivityDao = Mockito.mock(ActivityDao::class.java).also { dao -> runBlocking {
            Mockito.`when`(dao.pending(Mockito.anyString(), Mockito.anyInt())).thenAnswer {
                val limit = it.getArgument<Int>(1)
                requestedLimits += limit
                rows.filter { row -> states[row.eventId] == "PENDING" }.take(limit).also { page -> pageSizes += page.size }
            }
            if (acknowledges) Mockito.doAnswer { states[it.getArgument<String>(1)] = "UPLOADED"; null }
                .`when`(dao).acknowledge(Mockito.anyString(), Mockito.anyString())
            Mockito.doAnswer { states[it.getArgument<String>(1)] = "FAILED"; null }
                .`when`(dao).markFailed(Mockito.anyString(), Mockito.anyString())
        } }
    }

    @Test fun `backlog larger than a page drains fully in one pass, in bounded pages`() = runTest {
        val journal = Journal(events(250))
        val uploaded = mutableListOf<String>()
        ActivityEventSync(journal.dao, "A", ActivityUploader { _, event -> uploaded += event.eventId }, currentUid = { "A" })
            .retryPending()
        assertEquals(journal.rows.map { it.eventId }, uploaded)
        assertTrue(journal.states.values.all { it == "UPLOADED" })
        assertEquals(listOf(100, 100, 50), journal.pageSizes)
        assertEquals(List(3) { ACTIVITY_PENDING_PAGE_SIZE }, journal.requestedLimits)
        assertEquals(100, ACTIVITY_PENDING_PAGE_SIZE)
    }

    @Test fun `backlog that is an exact multiple of the page size ends on an empty page`() = runTest {
        val journal = Journal(events(200))
        ActivityEventSync(journal.dao, "A", ActivityUploader { _, _ -> }, currentUid = { "A" }).retryPending()
        assertEquals(listOf(100, 100, 0), journal.pageSizes)
        assertTrue(journal.states.values.all { it == "UPLOADED" })
    }

    @Test fun `terminal failure inside a page does not stop later pages`() = runTest {
        val journal = Journal(events(250))
        val uploaded = mutableListOf<String>()
        ActivityEventSync(journal.dao, "A", ActivityUploader { _, event ->
            if (event.eventId == "e005") throw firestore(Code.PERMISSION_DENIED)
            uploaded += event.eventId
        }, currentUid = { "A" }).retryPending()
        assertEquals("FAILED", journal.states["e005"])
        assertEquals(249, uploaded.size)
        assertEquals(249, journal.states.values.count { it == "UPLOADED" })
        assertEquals(listOf(100, 100, 50), journal.pageSizes)
    }

    @Test fun `transient failure on a later page stops the pass and leaves later pages untouched`() = runTest {
        val journal = Journal(events(250))
        val seen = mutableListOf<String>()
        try {
            ActivityEventSync(journal.dao, "A", ActivityUploader { _, event ->
                if (event.eventId == "e150") throw firestore(Code.UNAVAILABLE)
                seen += event.eventId
            }, currentUid = { "A" }).retryPending()
            fail("transient failures must propagate for backoff")
        } catch (failure: FirebaseFirestoreException) {
            assertEquals(Code.UNAVAILABLE, failure.code)
        }
        assertEquals(150, seen.size)
        assertEquals(2, journal.pageSizes.size)
        assertEquals(150, journal.states.values.count { it == "UPLOADED" })
        assertEquals(100, journal.states.values.count { it == "PENDING" })
    }

    @Test fun `rows that stay pending without throwing are not refetched endlessly`() = runTest {
        val journal = Journal(events(150), acknowledges = false)
        var attempts = 0
        ActivityEventSync(journal.dao, "A", ActivityUploader { _, _ -> attempts++ }, currentUid = { "A" }).retryPending()
        assertEquals(100, attempts)
        assertEquals(2, journal.pageSizes.size)
    }

    @Test fun `uid change mid-pass stops before the next row, even across pages`() = runTest {
        val journal = Journal(events(250))
        var currentUid = "A"
        val uploaded = mutableListOf<String>()
        ActivityEventSync(journal.dao, "A", ActivityUploader { _, event ->
            uploaded += event.eventId
            if (uploaded.size == 120) currentUid = "B"
        }, currentUid = { currentUid }).retryPending()
        assertEquals(120, uploaded.size)
        assertEquals(2, journal.pageSizes.size)
    }

    private class CoverageJournal(val rows: List<ActivityCoverageEntity>, acknowledges: Boolean = true) {
        val states = rows.associateWith { "PENDING" }.toMutableMap()
        val requestedLimits = mutableListOf<Int>()
        val pageSizes = mutableListOf<Int>()
        val dao: ActivityDao = Mockito.mock(ActivityDao::class.java).also { dao -> runBlocking {
            Mockito.`when`(dao.pending(Mockito.anyString(), Mockito.anyInt())).thenReturn(emptyList())
            Mockito.`when`(dao.pendingCoverage(Mockito.anyString(), Mockito.anyInt())).thenAnswer {
                val limit = it.getArgument<Int>(1)
                requestedLimits += limit
                rows.filter { row -> states[row] == "PENDING" }.take(limit).also { page -> pageSizes += page.size }
            }
            for (row in rows) {
                if (acknowledges) Mockito.doAnswer { states[row] = "UPLOADED"; null }.`when`(dao).acknowledgeCoverage(row)
                Mockito.doAnswer { states[row] = "FAILED"; null }.`when`(dao).markCoverageFailed(row)
            }
        } }
    }

    private fun coverage(count: Int): List<ActivityCoverageEntity> = (0 until count).map {
        ActivityCoverageEntity("account:A", "device", 20_000L + it, 20_000L + it, 1, false, "PENDING")
    }

    private fun repo(
        dao: ActivityDao,
        reporter: SyncFailureReporter = NoOpSyncFailureReporter,
        onCoverage: suspend (ActivityCoverageEntity) -> Unit,
    ) =
        FirestoreActivityRepository(dao, "A", object : ActivityRemoteClient {
            override suspend fun uploadEvent(uid: String, event: ActivityEventEntity) = Unit
            override suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity) = onCoverage(coverage)
            override fun observeEvents(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityEventEntity>())
            override fun observeCoverage(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityCoverageEntity>())
        }, currentUid = { "A" }, reporter = reporter)

    @Test fun `coverage backlog larger than a page drains fully in one pass, in bounded pages`() = runTest {
        val journal = CoverageJournal(coverage(250))
        val uploaded = mutableListOf<Long>()
        repo(journal.dao) { uploaded += it.fromEpochDay }.retryPending("account:A")
        assertEquals(journal.rows.map { it.fromEpochDay }, uploaded)
        assertTrue(journal.states.values.all { it == "UPLOADED" })
        assertEquals(listOf(100, 100, 50), journal.pageSizes)
        assertEquals(List(3) { ACTIVITY_PENDING_PAGE_SIZE }, journal.requestedLimits)
    }

    @Test fun `terminal coverage failure inside a page does not stop later pages`() = runTest {
        val journal = CoverageJournal(coverage(250))
        repo(journal.dao) { if (it.fromEpochDay == 20_005L) throw firestore(Code.INVALID_ARGUMENT) }
            .retryPending("account:A")
        assertEquals("FAILED", journal.states[journal.rows[5]])
        assertEquals(249, journal.states.values.count { it == "UPLOADED" })
    }

    @Test fun `transient coverage failure on a later page stops the pass`() = runTest {
        val journal = CoverageJournal(coverage(250))
        try {
            repo(journal.dao) { if (it.fromEpochDay == 20_150L) throw firestore(Code.UNAVAILABLE) }
                .retryPending("account:A")
            fail("transient failures must propagate")
        } catch (failure: FirebaseFirestoreException) {
            assertEquals(Code.UNAVAILABLE, failure.code)
        }
        assertEquals(2, journal.pageSizes.size)
        assertEquals(100, journal.states.values.count { it == "PENDING" })
    }

    @Test fun `coverage that stays pending without throwing is not refetched endlessly`() = runTest {
        val journal = CoverageJournal(coverage(150), acknowledges = false)
        var attempts = 0
        repo(journal.dao) { attempts++ }.retryPending("account:A")
        assertEquals(100, attempts)
        assertEquals(2, journal.pageSizes.size)
    }

    @Test fun `tracker only ever holds the previous page keys`() {
        val tracker = PendingPageTracker<String>()
        var maxTracked = 0
        for (page in 0 until 50) {
            val keys = (0 until ACTIVITY_PENDING_PAGE_SIZE).map { "p$page-$it" }
            assertFalse(tracker.stalled(keys))
            tracker.remember(keys)
            maxTracked = maxOf(maxTracked, tracker.trackedCount)
        }
        assertEquals(ACTIVITY_PENDING_PAGE_SIZE, maxTracked)
    }

    @Test fun `tracker reports no progress only when a key repeats from the previous page`() {
        val tracker = PendingPageTracker<String>()
        tracker.remember(listOf("a", "b"))
        assertFalse(tracker.stalled(listOf("c", "d")))
        assertTrue(tracker.stalled(listOf("c", "b")))
    }

    @Test fun `rows leaving pending between pages are never misread as no progress`() = runTest {
        val journal = Journal(events(450))
        var uploads = 0
        ActivityEventSync(journal.dao, "A", ActivityUploader { _, _ -> uploads++ }, currentUid = { "A" }).retryPending()
        assertEquals(450, uploads)
        assertEquals(listOf(100, 100, 100, 100, 50), journal.pageSizes)
    }

    private class RecordingReporter(private val failWith: Throwable? = null) : SyncFailureReporter {
        val reports = mutableListOf<SyncFailureReport>()
        override fun report(report: SyncFailureReport) {
            reports += report
            failWith?.let { throw it }
        }
    }

    @Test fun `stalled event pass reports PAGE_STALL once with the row kind and nothing identifying`() = runTest {
        val journal = Journal(events(150), acknowledges = false)
        val reporter = RecordingReporter()
        ActivityEventSync(journal.dao, "A", ActivityUploader { _, _ -> }, currentUid = { "A" }, reporter = reporter)
            .retryPending()
        assertEquals(listOf(SyncFailureReport(SyncFailureKind.PAGE_STALL, "PendingPageStall", null, TerminalRowKind.EVENT)),
            reporter.reports)
    }

    @Test fun `stalled coverage pass reports PAGE_STALL once with the row kind`() = runTest {
        val journal = CoverageJournal(coverage(150), acknowledges = false)
        val reporter = RecordingReporter()
        repo(journal.dao, reporter) { }.retryPending("account:A")
        assertEquals(listOf(SyncFailureReport(SyncFailureKind.PAGE_STALL, "PendingPageStall", null, TerminalRowKind.COVERAGE)),
            reporter.reports)
    }

    @Test fun `repeated stalled passes reach the delegate once through the once-per-run reporter`() = runTest {
        val journal = Journal(events(150), acknowledges = false)
        val delegate = RecordingReporter()
        val sync = ActivityEventSync(journal.dao, "A", ActivityUploader { _, _ -> }, currentUid = { "A" },
            reporter = OncePerRunSyncFailureReporter(delegate))
        sync.retryPending()
        sync.retryPending()
        assertEquals(1, delegate.reports.size)
    }

    @Test fun `an event stall and a coverage stall in the same run both reach the delegate`() = runTest {
        val delegate = RecordingReporter()
        val once = OncePerRunSyncFailureReporter(delegate)
        ActivityEventSync(Journal(events(150), acknowledges = false).dao, "A", ActivityUploader { _, _ -> },
            currentUid = { "A" }, reporter = once).retryPending()
        repo(CoverageJournal(coverage(150), acknowledges = false).dao, once) { }.retryPending("account:A")
        assertEquals(listOf(TerminalRowKind.EVENT, TerminalRowKind.COVERAGE), delegate.reports.map { it.rowKind })
    }

    @Test fun `normal drains do not report a stall`() = runTest {
        val eventReporter = RecordingReporter()
        ActivityEventSync(Journal(events(250)).dao, "A", ActivityUploader { _, _ -> }, currentUid = { "A" },
            reporter = eventReporter).retryPending()
        val coverageReporter = RecordingReporter()
        repo(CoverageJournal(coverage(250)).dao, coverageReporter) { }.retryPending("account:A")
        assertTrue(eventReporter.reports.isEmpty())
        assertTrue(coverageReporter.reports.isEmpty())
    }

    @Test fun `a throwing reporter does not change the stop behavior`() = runTest {
        val journal = Journal(events(150), acknowledges = false)
        var attempts = 0
        ActivityEventSync(journal.dao, "A", ActivityUploader { _, _ -> attempts++ }, currentUid = { "A" },
            reporter = RecordingReporter(IllegalStateException("backend down"))).retryPending()
        assertEquals(100, attempts)
        assertEquals(2, journal.pageSizes.size)
    }

    /** Orders pending rows like the SQL (occurredAtMillis, eventId) and accepts inserts mid-pass. */
    private class SortedJournal(initial: List<ActivityEventEntity>) {
        val rows = initial.toMutableList()
        val states = initial.associate { it.eventId to "PENDING" }.toMutableMap()
        val dao: ActivityDao = Mockito.mock(ActivityDao::class.java).also { dao -> runBlocking {
            Mockito.`when`(dao.pending(Mockito.anyString(), Mockito.anyInt())).thenAnswer {
                rows.filter { row -> states[row.eventId] == "PENDING" }
                    .sortedWith(compareBy({ row -> row.occurredAtMillis }, { row -> row.eventId }))
                    .take(it.getArgument<Int>(1))
            }
            Mockito.doAnswer { states[it.getArgument<String>(1)] = "UPLOADED"; null }
                .`when`(dao).acknowledge(Mockito.anyString(), Mockito.anyString())
        } }
        fun insert(event: ActivityEventEntity) { rows += event; states[event.eventId] = "PENDING" }
    }

    @Test fun `an older event inserted after page one was fetched uploads once without a false stall`() = runTest {
        val journal = SortedJournal(events(250).mapIndexed { i, e -> e.copy(occurredAtMillis = 10_000L + i) })
        val reporter = RecordingReporter()
        val uploaded = mutableListOf<String>()
        ActivityEventSync(journal.dao, "A", ActivityUploader { _, event ->
            uploaded += event.eventId
            if (uploaded.size == 10) journal.insert(template.copy(eventId = "older", occurredAtMillis = 1L))
        }, currentUid = { "A" }, reporter = reporter).retryPending()
        assertEquals(251, uploaded.size)
        assertEquals(251, uploaded.toSet().size)
        assertTrue(journal.states.values.all { it == "UPLOADED" })
        assertEquals(100, uploaded.indexOf("older"))
        assertTrue(reporter.reports.isEmpty())
    }

    @Test fun `coverage rows differing only in tiebreak columns across a page boundary upload once each`() = runTest {
        val rows = (0 until 150).map {
            ActivityCoverageEntity("account:A", "src%02d".format(it / 6), 20_000L, 20_000L, 1 + it % 3, (it / 3) % 2 == 0, "PENDING")
        }.distinct().sortedWith(compareBy({ it.fromEpochDay }, { it.toEpochDay }, { it.sourceId }, { it.recordingSchema }, { it.uncertain }))
        assertTrue(rows.size > ACTIVITY_PENDING_PAGE_SIZE)
        val states = rows.associateWith { "PENDING" }.toMutableMap()
        val dao: ActivityDao = Mockito.mock(ActivityDao::class.java)
        Mockito.`when`(dao.pending(Mockito.anyString(), Mockito.anyInt())).thenReturn(emptyList())
        Mockito.`when`(dao.pendingCoverage(Mockito.anyString(), Mockito.anyInt())).thenAnswer {
            rows.filter { row -> states[row] == "PENDING" }.take(it.getArgument<Int>(1))
        }
        for (row in rows) Mockito.doAnswer { states[row] = "UPLOADED"; null }.`when`(dao).acknowledgeCoverage(row)
        val reporter = RecordingReporter()
        val uploaded = mutableListOf<ActivityCoverageEntity>()
        repo(dao, reporter) { uploaded += it }.retryPending("account:A")
        assertEquals(rows, uploaded)
        assertTrue(states.values.all { it == "UPLOADED" })
        assertTrue(reporter.reports.isEmpty())
    }

    @Test fun `cancellation on page two propagates, keeps page one acknowledged and reports nothing`() = runTest {
        val journal = Journal(events(250))
        val reporter = RecordingReporter()
        var uploads = 0
        try {
            ActivityEventSync(journal.dao, "A", ActivityUploader { _, _ ->
                if (++uploads == 120) throw CancellationException("cancelled")
            }, currentUid = { "A" }, reporter = reporter).retryPending()
            fail("cancellation must propagate")
        } catch (cancellation: CancellationException) {
            assertEquals("cancelled", cancellation.message)
        }
        assertEquals(119, journal.states.values.count { it == "UPLOADED" })
        assertTrue(journal.rows.take(100).all { journal.states[it.eventId] == "UPLOADED" })
        assertEquals(131, journal.states.values.count { it == "PENDING" })
        assertEquals("PENDING", journal.states["e119"])
        assertTrue(reporter.reports.isEmpty())
    }
}
