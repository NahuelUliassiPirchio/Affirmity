package com.pirxhio.affirmity.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exercises production transaction bodies; real Room SQL remains covered on device separately. */
class ActivityOwnerTransactionTest {
    @Test(expected = IllegalArgumentException::class)
    fun `invalid source cannot initialize an anonymous generation`() = runBlocking {
        MemoryDao().anonymousOwner(ActivityOwnerEntity("local:first", "", null, true))
        Unit
    }

    @Test fun `claim is idempotent and rotation retains the original account`() = runBlocking {
        val dao = MemoryDao()
        val first = ActivityOwnerEntity("local:first", "device", null, true)
        assertEquals(first, dao.anonymousOwner(first))
        assertEquals(first, dao.anonymousOwner(first.copy(owner = "local:unused")))
        assertEquals(first.copy(claimedUid = "A", active = false), dao.claimAnonymous(first.owner, "A"))
        assertEquals("A", dao.claimAnonymous(first.owner, "A").claimedUid)
        val second = first.copy(owner = "local:second")
        assertEquals(second, dao.anonymousOwner(second))
        assertEquals("A", dao.owner(first.owner)?.claimedUid)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `claim cannot transfer an anonymous generation from A to B`() = runBlocking {
        val dao = MemoryDao()
        val first = ActivityOwnerEntity("local:first", "device", null, true)
        dao.anonymousOwner(first)
        dao.claimAnonymous(first.owner, "A")
        dao.claimAnonymous(first.owner, "B")
        Unit
    }

    @Test fun `claimed generation rejects new events but accepts identical durable retries`() = runBlocking {
        val dao = MemoryDao()
        dao.anonymousOwner(ActivityOwnerEntity("local:first", "device", null, true))
        val event = ActivityEventEntity("local:first", "view", 20_000, "view", 100, 100, 100,
            "UTC", null, null, 0, "device", "LOCAL")
        dao.record(event)
        dao.claimAnonymous("local:first", "A")
        assertEquals(event, dao.record(event))
        val failure = runCatching { dao.record(event.copy(eventId = "late")) }.exceptionOrNull()
        org.junit.Assert.assertTrue(failure is IllegalArgumentException)
        assertEquals(listOf(event), dao.getRange("local:first", 20_000, 20_000))
    }

    @Test fun `claimed generation rejects new coverage but accepts original interval retry`() = runBlocking {
        val dao = MemoryDao()
        dao.anonymousOwner(ActivityOwnerEntity("local:first", "device", null, true))
        val interval = ActivityCoverageEntity("local:first", "device", 20_000, 20_000, 1, false, "LOCAL")
        dao.recordCoverage(interval)
        dao.claimAnonymous("local:first", "A")
        assertEquals(-1L, dao.recordCoverage(interval))
        val failure = runCatching { dao.recordCoverage(interval.copy(toEpochDay = 20_001)) }.exceptionOrNull()
        org.junit.Assert.assertTrue(failure is IllegalArgumentException)
        assertEquals(listOf(interval), dao.coverageRange("local:first", 20_000, 20_001))
    }

    private class MemoryDao : ActivityDao() {
        private val coverages = mutableListOf<ActivityCoverageEntity>()
        private val events = mutableMapOf<Pair<String, String>, ActivityEventEntity>()
        private val owners = mutableMapOf<String, ActivityOwnerEntity>()
        override suspend fun owner(owner: String) = owners[owner]
        override suspend fun claimedOwners(uid: String) = owners.values.filter { it.claimedUid == uid }
        override suspend fun activeAnonymous() = owners.values.firstOrNull { it.active && it.claimedUid == null }
        override suspend fun insertOwner(owner: ActivityOwnerEntity) {
            check(owner.owner !in owners)
            owners[owner.owner] = owner
        }
        override suspend fun assignOwner(owner: String, uid: String) {
            owners[owner] = owners.getValue(owner).copy(claimedUid = uid, active = false)
        }
        override suspend fun insertEvent(event: ActivityEventEntity): Long {
            val key = event.owner to event.eventId
            if (key in events) return -1L
            events[key] = event
            return 1L
        }
        override suspend fun get(owner: String, eventId: String) = events[owner to eventId]
        override suspend fun getRange(owner: String, from: Long, to: Long) = events.values.filter { it.owner == owner && it.epochDay in from..to }
        override fun observeRange(owner: String, from: Long, to: Long): Flow<List<ActivityEventEntity>> = flowOf(emptyList())
        override suspend fun pending(owner: String): List<ActivityEventEntity> = error("Not used")
        override suspend fun acknowledge(owner: String, eventId: String) = Unit
        override suspend fun markFailed(owner: String, eventId: String) = Unit
        override suspend fun rearmFailed(owner: String) = Unit
        override suspend fun rearmFailedCoverage(owner: String) = Unit
        override suspend fun insertCoverage(coverage: ActivityCoverageEntity): Long {
            if (coverage in coverages) return -1L
            coverages.add(coverage)
            return 1L
        }
        override suspend fun coverageRange(owner: String, from: Long, to: Long) = coverages.filter { it.owner == owner && it.fromEpochDay <= to && it.toEpochDay >= from }
        override fun observeCoverage(owner: String, from: Long, to: Long): Flow<List<ActivityCoverageEntity>> = flowOf(emptyList())
        override suspend fun pendingCoverage(owner: String): List<ActivityCoverageEntity> = error("Not used")
        override suspend fun markCoverageUploaded(owner: String, sourceId: String, schema: Int, from: Long, to: Long, uncertain: Boolean) = Unit
        override suspend fun markCoverageFailedRow(owner: String, sourceId: String, schema: Int, from: Long, to: Long, uncertain: Boolean) = Unit
    }
}
