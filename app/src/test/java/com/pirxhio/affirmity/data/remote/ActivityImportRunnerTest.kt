package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityImportRunnerTest {
    private val store = FakeActivityStore()
    private val source = CountingSource()
    private val reports = mutableListOf<ActivityImportStats>()
    private val runner = ActivityImportRunner(
        ActivityHistoryImporter(source),
        object : ActivityImportStore {
            override suspend fun pendingOwners(uid: String) = store.dao.pendingImportOwners(uid)
            override suspend fun claimedCount(uid: String) = store.dao.countClaimed(uid)
            override suspend fun snapshot(owner: String, uid: String) = store.lifecycle.claim(owner, uid)
            override suspend fun markImported(owner: String, uid: String, atMillis: Long) =
                store.dao.markImported(owner, uid, atMillis)
        },
        nowMillis = { 1_000L },
        report = { reports += it },
    )

    private fun claimedEmpty(name: String, imported: Long? = null) {
        store.owners[name] = ActivityOwnerEntity(name, "device", "A", false, true, name, imported)
    }

    private fun claimedWithEvent(name: String, imported: Long? = null) {
        claimedEmpty(name, imported)
        store.events[name to name] = ActivityEventEntity(name, name, 20_000, "view", 100, 100, 100,
            "UTC", null, null, 0, "device", "LOCAL")
    }

    private suspend fun runPass() = runner.run("A") { "A" }

    @Test fun `an already imported owner causes no network call`() = runBlocking {
        claimedWithEvent("local:done", imported = 5L)
        runPass()
        assertEquals(0, source.calls)
        assertEquals(ActivityImportStats(claimed = 1, skippedImported = 1), reports.single())
    }

    @Test fun `launches read only pending owners so cost does not grow with sealed history`() = runBlocking {
        repeat(50) { claimedEmpty("local:old-$it", imported = 5L) }
        claimedEmpty("local:new")
        runPass()
        assertEquals(0, source.calls)
        assertEquals(ActivityImportStats(claimed = 51, skippedImported = 50, skippedEmpty = 1), reports.single())
    }

    @Test fun `repeated signed-in launches make no network call after the first import`() = runBlocking {
        var callsAfterFirst = -1
        repeat(6) { launch ->
            val owner = store.lifecycle.initialize(ActivityOwnerEntity("local:launch-$launch", "device", null, true))
            if (launch == 0) store.dao.record(ActivityEventEntity(owner.owner, "view", 20_000, "view", 100, 100, 100,
                "UTC", null, null, 0, "device", "LOCAL"))
            store.lifecycle.claim(owner.owner, "A")
            runPass()
            if (launch == 0) callsAfterFirst = source.calls
        }
        assertTrue(callsAfterFirst > 0)
        assertEquals(callsAfterFirst, source.calls)
        assertEquals(6, store.dao.claimedOwners("A").size)
        assertEquals(0, store.dao.pendingImportOwners("A").size)
        assertEquals(ActivityImportStats(claimed = 6, skippedImported = 5, skippedEmpty = 1), reports.last())
    }

    @Test fun `an empty claimed owner is marked locally with zero network calls`() = runBlocking {
        claimedEmpty("local:empty")
        runPass()
        assertEquals(0, source.calls)
        assertEquals(1_000L, store.owners.getValue("local:empty").importedAtMillis)
        assertEquals(ActivityImportStats(claimed = 1, skippedEmpty = 1), reports.single())
    }

    @Test fun `an owner with data is imported once and never touches the network again`() = runBlocking {
        claimedWithEvent("local:data")
        runPass()
        assertEquals(1, source.markerChecks)
        assertEquals(1, source.acknowledged.size)
        assertEquals(1_000L, store.owners.getValue("local:data").importedAtMillis)
        val callsAfterFirst = source.calls
        runPass()
        assertEquals(callsAfterFirst, source.calls)
        assertEquals(ActivityImportStats(claimed = 1, networkChecked = 1), reports.first())
        assertEquals(ActivityImportStats(claimed = 1, skippedImported = 1), reports.last())
    }

    @Test fun `a server marker found for an owner with data is remembered locally`() = runBlocking {
        claimedWithEvent("local:data")
        source.preMarked += "local:data"
        runPass()
        assertEquals(0, source.acknowledged.size)
        assertNotNull(store.owners.getValue("local:data").importedAtMillis)
    }

    @Test fun `a failure midway keeps the earlier owners marked and does not mark the failed one`() = runBlocking {
        claimedEmpty("local:a-empty")
        claimedWithEvent("local:b-data")
        claimedWithEvent("local:c-boom")
        claimedEmpty("local:d-after")
        source.failEventsFor = "local:c-boom"
        assertTrue(runCatching { runPass() }.isFailure)
        assertNotNull(store.owners.getValue("local:a-empty").importedAtMillis)
        assertNotNull(store.owners.getValue("local:b-data").importedAtMillis)
        assertNull(store.owners.getValue("local:c-boom").importedAtMillis)
        assertNull(store.owners.getValue("local:d-after").importedAtMillis)
        assertEquals(2, reports.single().networkChecked)

        source.failEventsFor = null
        val before = source.calls
        runPass()
        assertTrue(source.calls > before)
        assertTrue(store.owners.values.all { it.importedAtMillis != null })
        assertEquals(ActivityImportStats(claimed = 4, skippedImported = 2, skippedEmpty = 1, networkChecked = 1), reports.last())
    }

    private class CountingSource : ActivityImportSource {
        var calls = 0
        var markerChecks = 0
        val preMarked = mutableSetOf<String>()
        val acknowledged = mutableListOf<String>()
        var failEventsFor: String? = null
        override suspend fun markerExists(uid: String, generation: String): Boolean {
            calls++; markerChecks++
            return generation in preMarked || generation in acknowledged
        }
        override suspend fun writeFacts(writes: List<DocWrite>) { calls++ }
        override suspend fun uploadEvent(uid: String, event: ActivityEventEntity) {
            calls++
            if (event.eventId == failEventsFor) error("Offline")
        }
        override suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity) { calls++ }
        override suspend fun acknowledge(uid: String, generation: String) { calls++; acknowledged += generation }
    }
}
