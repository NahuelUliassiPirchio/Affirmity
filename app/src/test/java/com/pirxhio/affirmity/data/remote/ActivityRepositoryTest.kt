package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.ActivityEvent
import com.pirxhio.affirmity.data.ActivityKind
import com.pirxhio.affirmity.data.ActivitySaveState
import com.pirxhio.affirmity.data.ActivityRangeState
import com.pirxhio.affirmity.data.ActivityCoverage
import com.pirxhio.affirmity.data.local.ActivityDao
import com.pirxhio.affirmity.data.local.ActivityEventEntity
import com.pirxhio.affirmity.data.local.ActivityCoverageEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class ActivityRepositoryTest {
    private val entity = ActivityEventEntity("account:A", "session", 20_000, "guided", 1_000,
        0, 1_000, "UTC", "breathing", "Breathing", 120, "device", "PENDING")
    private val command = ActivityEvent("account:A", "session", 20_000, ActivityKind.GUIDED,
        1_000, 0, 1_000, "UTC", "breathing", "Breathing", 120, "device")

    @Test fun `record acknowledges durable local pending state before network upload`() = runTest {
        val dao = Mockito.mock(ActivityDao::class.java)
        Mockito.`when`(dao.record(entity)).thenReturn(entity)
        val repo = FirestoreActivityRepository(dao, "A", TestRemote(), { "A" })
        assertEquals(ActivitySaveState.PENDING, repo.record(command))
        Mockito.verify(dao).record(entity)
    }

    @Test fun `uploaded local fact survives acknowledgement before remote listener catches up`() = runTest {
        val dao = Mockito.mock(ActivityDao::class.java)
        Mockito.`when`(dao.observeRange("account:A", 20_000, 20_001)).thenReturn(flowOf(listOf(entity.copy(uploadState = "UPLOADED"))))
        Mockito.`when`(dao.observeCoverage("account:A", 20_000, 20_001)).thenReturn(flowOf(emptyList()))
        val states = FirestoreActivityRepository(dao, "A", TestRemote(), { "A" })
            .observeRange("account:A", 20_000, 20_001).toList()
        val ready = states.last() as ActivityRangeState.Ready
        assertEquals(1, ready.days.getValue(20_000).sessions.size)
        assertEquals(120L, ready.days.getValue(20_000).activeSeconds)
        assertEquals(0, ready.pendingCount)
        assertEquals(ActivityCoverage.UNKNOWN, ready.days.getValue(20_001).coverage)
    }

    @Test fun `range unions pending with remote without duplicates or account completeness`() = runTest {
        val dao = Mockito.mock(ActivityDao::class.java)
        Mockito.`when`(dao.observeRange("account:A", 20_000, 20_001)).thenReturn(flowOf(listOf(entity)))
        Mockito.`when`(dao.observeCoverage("account:A", 20_000, 20_001)).thenReturn(flowOf(emptyList()))
        val repo = FirestoreActivityRepository(dao, "A", TestRemote(listOf(entity.copy(uploadState = "UPLOADED"))), { "A" })
        val states = repo.observeRange("account:A", 20_000, 20_001).toList()
        assertEquals(ActivityRangeState.Loading, states.first())
        val ready = states.last() as ActivityRangeState.Ready
        assertEquals(120L, ready.days.getValue(20_000).activeSeconds)
        assertEquals(1, ready.days.getValue(20_000).sessions.size)
        assertEquals(ActivityCoverage.UNKNOWN, ready.days.getValue(20_001).coverage)
        assertEquals(false, ready.days.getValue(20_001).confirmedEmpty)
        assertEquals(1, ready.pendingCount)
    }

    @Test(expected = IllegalStateException::class)
    fun `failed local persistence is not reported as saved`() = runTest {
        val dao = Mockito.mock(ActivityDao::class.java)
        Mockito.`when`(dao.record(entity)).thenThrow(IllegalStateException("Disk full"))
        FirestoreActivityRepository(dao, "A", TestRemote(), { "A" }).record(command)
        Unit
    }

    @Test fun `remote range failure stays error rather than empty ready`() = runTest {
        val dao = Mockito.mock(ActivityDao::class.java)
        Mockito.`when`(dao.observeRange("account:A", 20_000, 20_001)).thenReturn(flowOf(emptyList()))
        Mockito.`when`(dao.observeCoverage("account:A", 20_000, 20_001)).thenReturn(flowOf(emptyList()))
        val repo = FirestoreActivityRepository(dao, "A", TestRemote(failure = IllegalStateException("Missing index")), { "A" })
        val states = repo.observeRange("account:A", 20_000, 20_001).toList()
        assertEquals("Missing index", (states.last() as ActivityRangeState.Error).cause.message)
    }

    @Test fun `stale A repository does not expose A range when B is active`() = runTest {
        val dao = Mockito.mock(ActivityDao::class.java)
        val repo = FirestoreActivityRepository(dao, "A", TestRemote(listOf(entity)), { "B" })
        assertEquals(listOf(ActivityRangeState.Loading), repo.observeRange("account:A", 20_000, 20_001).toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `range cannot silently expand to unbounded history`() {
        val dao = Mockito.mock(ActivityDao::class.java)
        FirestoreActivityRepository(dao, "A", TestRemote(), { "A" }).observeRange("account:A", 20_000, 20_042)
    }

    private class TestRemote(private val events: List<ActivityEventEntity> = emptyList(), private val failure: Throwable? = null) : ActivityRemoteClient {
        override suspend fun uploadEvent(uid: String, event: ActivityEventEntity) = error("No network before durable acknowledgement")
        override suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity) = error("Not used")
        override fun observeEvents(uid: String, from: Long, to: Long) = flow {
            failure?.let { throw it }
            emit(events)
        }
        override fun observeCoverage(uid: String, from: Long, to: Long) = flowOf(emptyList<ActivityCoverageEntity>())
    }
}
