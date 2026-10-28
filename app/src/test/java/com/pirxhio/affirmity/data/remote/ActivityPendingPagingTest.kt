package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.pirxhio.affirmity.data.local.ACTIVITY_PENDING_PAGE_SIZE
import com.pirxhio.affirmity.data.local.ActivityCoverageEntity
import com.pirxhio.affirmity.data.local.ActivityDao
import com.pirxhio.affirmity.data.local.ActivityEventEntity
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

    private fun repo(dao: ActivityDao, onCoverage: suspend (ActivityCoverageEntity) -> Unit) =
        FirestoreActivityRepository(dao, "A", object : ActivityRemoteClient {
            override suspend fun uploadEvent(uid: String, event: ActivityEventEntity) = Unit
            override suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity) = onCoverage(coverage)
            override fun observeEvents(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityEventEntity>())
            override fun observeCoverage(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityCoverageEntity>())
        }, currentUid = { "A" })

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
}
