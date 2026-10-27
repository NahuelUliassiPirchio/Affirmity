package com.pirxhio.affirmity.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Drives the production `mutate` body; guards the typed exception callers rely on to swallow stale writes. */
class ActivityDailyFactDaoMutateTest {
    private fun owner(
        active: Boolean = true,
        claimedUid: String? = null,
        adopted: Boolean = true,
    ) = ActivityOwnerEntity("local:A", "device", claimedUid, active, adopted)

    private fun assertNotWritable(state: ActivityOwnerEntity) = runBlocking {
        val dao = MemoryDao(state)
        try {
            dao.mutate(state.owner, 100, DailyFactChange.Meditation)
            fail("expected AnonymousGenerationClaimedException")
        } catch (_: AnonymousGenerationClaimedException) {
        }
        assertTrue(dao.written.isEmpty())
    }

    @Test fun `claimed owner throws the typed exception`() = assertNotWritable(owner(active = false, claimedUid = "uid"))

    @Test fun `inactive owner throws the typed exception`() = assertNotWritable(owner(active = false))

    @Test fun `owner with incomplete legacy adoption throws the typed exception`() = assertNotWritable(owner(adopted = false))

    @Test fun `writable owner persists the change`() = runBlocking {
        val dao = MemoryDao(owner())
        dao.mutate("local:A", 100, DailyFactChange.Meditation)
        assertNotNull(dao.written.singleOrNull())
        assertEquals(true, dao.written.single().meditationDone)
    }

    private class MemoryDao(private val state: ActivityOwnerEntity) : ActivityDailyFactDao() {
        val written = mutableListOf<ActivityDailyFactEntity>()
        override suspend fun getRange(owner: String, from: Long, to: Long) = written.filter { it.owner == owner && it.epochDay in from..to }
        override fun observeRange(owner: String, from: Long, to: Long): Flow<List<ActivityDailyFactEntity>> = flowOf(written)
        override suspend fun getAll(owner: String) = written.toList()
        override suspend fun ownerState(owner: String) = state.takeIf { it.owner == owner }
        override suspend fun firstAdopter(): String? = null
        override suspend fun completeAdoption(owner: String, adopter: String) = Unit
        override suspend fun legacyMoods() = emptyList<DailyMoodEntity>()
        override suspend fun legacyCompletions() = emptyList<DailyCompletionEntity>()
        override suspend fun legacyHealers() = emptyList<StreakHealerUseEntity>()
        override suspend fun write(fact: ActivityDailyFactEntity) {
            written.removeAll { it.owner == fact.owner && it.epochDay == fact.epochDay }
            written += fact
        }
    }
}
