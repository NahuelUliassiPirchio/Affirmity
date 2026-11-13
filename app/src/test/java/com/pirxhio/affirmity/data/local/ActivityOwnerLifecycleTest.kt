package com.pirxhio.affirmity.data.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives the production launch/claim bodies across simulated process starts. */
class ActivityOwnerLifecycleTest {
    private fun candidate(n: Int) = ActivityOwnerEntity("local:launch-$n", "device", null, true)

    /** One signed-in cold start: the local session initializes its generation, then the swap claims it. */
    private suspend fun signedInLaunch(store: FakeActivityStore, n: Int, uid: String) {
        val owner = store.lifecycle.initialize(candidate(n))
        store.lifecycle.claim(owner.owner, uid)
    }

    @Test fun `every signed-in launch seals its generation even when it is empty`() = runBlocking {
        val store = FakeActivityStore()
        repeat(3) { signedInLaunch(store, it, "A") }
        assertEquals(3, store.dao.claimedOwners("A").size)
        assertTrue(store.owners.values.none { it.active })
    }

    @Test fun `a generation with activity data is claimed once and keeps its snapshot`() = runBlocking {
        val store = FakeActivityStore()
        val first = store.lifecycle.initialize(candidate(0))
        store.dao.record(ActivityEventEntity(first.owner, "view", 20_000, "view", 100, 100, 100,
            "UTC", null, null, 0, "device", "LOCAL"))
        val snapshot = store.lifecycle.claim(first.owner, "A")
        assertEquals("A", snapshot.owner.claimedUid)
        assertEquals(1, snapshot.events.size)
        assertEquals("A", store.lifecycle.claim(first.owner, "A").owner.claimedUid)
        assertEquals(1, store.dao.claimedOwners("A").count { it.owner == first.owner })
    }

    @Test fun `a generation with only daily facts or coverage still counts as data`() = runBlocking {
        val withFact = FakeActivityStore()
        val a = withFact.lifecycle.initialize(candidate(0))
        withFact.facts += ActivityDailyFactEntity(a.owner, 20_000, moodValue = 3)
        assertEquals("A", withFact.lifecycle.claim(a.owner, "A").owner.claimedUid)

        val withCoverage = FakeActivityStore()
        val b = withCoverage.lifecycle.initialize(candidate(0))
        withCoverage.dao.recordCoverage(ActivityCoverageEntity(b.owner, "device", 20_000, 20_000, 1, false, "LOCAL"))
        assertEquals("A", withCoverage.lifecycle.claim(b.owner, "A").owner.claimedUid)
    }

    @Test fun `an already claimed generation is returned as claimed even when empty`() = runBlocking {
        val store = FakeActivityStore()
        store.owners["local:old"] = ActivityOwnerEntity("local:old", "device", "A", false, true, "local:old")
        val snapshot = store.lifecycle.claim("local:old", "A")
        assertEquals("A", snapshot.owner.claimedUid)
        assertTrue(snapshot.dailyFacts.isEmpty() && snapshot.events.isEmpty() && snapshot.coverage.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a claimed generation still cannot be reassigned from A to B`() = runBlocking {
        val store = FakeActivityStore()
        val owner = store.lifecycle.initialize(candidate(0))
        store.dao.record(ActivityEventEntity(owner.owner, "view", 20_000, "view", 100, 100, 100,
            "UTC", null, null, 0, "device", "LOCAL"))
        store.lifecycle.claim(owner.owner, "A")
        store.lifecycle.claim(owner.owner, "B")
        Unit
    }

    @Test fun `a write captured before A's claim cannot leak into B's import after sign-out`() = runBlocking {
        val store = FakeActivityStore()
        // Cold start signed in as A: the Local session owns generation G and is claimed by the swap.
        val g = store.lifecycle.initialize(candidate(0))
        store.lifecycle.claim(g.owner, "A")
        // A stale writer that captured the Local session before the swap lands AFTER the claim.
        val late = ActivityEventEntity(g.owner, "late-view", 20_000, "view", 100, 100, 100,
            "UTC", null, null, 0, "device", "LOCAL")
        val rejected = runCatching { store.dao.record(late) }.exceptionOrNull()
        val lateFact = runCatching { store.dailyFacts.mutate(g.owner, 20_000, DailyFactChange.Mood(2, "A's private note")) }.exceptionOrNull()
        assertTrue("late event must hit the claim barrier", rejected is IllegalArgumentException)
        assertTrue("late fact must hit the claim barrier", lateFact is AnonymousGenerationClaimedException)
        // A signs out (a fresh local session starts), then B signs in.
        val afterSignOut = store.lifecycle.initialize(candidate(1))
        val snapshot = store.lifecycle.claim(afterSignOut.owner, "B")
        assertTrue(snapshot.events.isEmpty() && snapshot.dailyFacts.isEmpty())
        val bData = store.dao.claimedOwners("B").map { it.owner }.flatMap { owner ->
            store.dao.getRange(owner, Long.MIN_VALUE, Long.MAX_VALUE) + store.dailyFacts.getAll(owner)
        }
        assertTrue(bData.isEmpty())
        assertEquals("A", store.owners.getValue(g.owner).claimedUid)
    }
}
