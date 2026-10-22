package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.local.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ActivityHistoryImportTest {
    private fun snapshot() = ActivityClaimSnapshot(
        ActivityOwnerEntity("local:G", "device", "A", false, true, "local:G"),
        listOf(ActivityDailyFactEntity("local:G", 10, 2, "original", true, true, false, 100)),
        listOf(ActivityEventEntity("local:G", "event", 10, "view", 1, 1, 1, "UTC", null, null, 0, "device", "LOCAL")),
        emptyList(),
    )

    @Test fun `interrupted import retries originals but preserves account mood and acknowledges marker last`() = runTest {
        val source = MemorySource()
        val importer = ActivityHistoryImporter(source)
        assertTrue(runCatching { importer.import(snapshot(), "A") { "A" } }.isFailure)
        assertFalse(source.marked)
        source.destinationMood = mapOf("epochDay" to 10L, "moodValue" to 5)
        source.failEvent = false
        importer.import(snapshot(), "A") { "A" }
        assertTrue(source.marked)
        assertEquals(5L, source.destinationMood?.get("moodValue"))
        assertEquals(listOf("A:event"), source.events.toList())
        assertEquals("marker", source.operations.last())
        val count = source.operations.size
        importer.import(snapshot(), "A") { "A" }
        assertEquals(count, source.operations.size)
    }

    @Test fun `foreign claim is rejected before any destination operation`() = runTest {
        val source = MemorySource()
        assertTrue(runCatching { ActivityHistoryImporter(source).import(snapshot(), "B") { "B" } }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, source.operations.size)
    }

    private class MemorySource : ActivityImportSource {
        var marked = false
        var failEvent = true
        var destinationMood: Map<String, Any>? = null
        val events = linkedSetOf<String>()
        val operations = mutableListOf<String>()
        override suspend fun markerExists(uid: String, generation: String) = marked
        override suspend fun writeFacts(writes: List<DocWrite>) {
            operations += "facts"
            for (write in writes) if (write.path.contains("/dailyMoods/")) {
                resolveMigrationWrite(write, destinationMood)?.let { destinationMood = it }
            }
        }
        override suspend fun uploadEvent(uid: String, event: ActivityEventEntity) {
            if (failEvent) error("Offline")
            assertEquals("account:$uid", event.owner)
            events += "$uid:${event.eventId}"
            operations += "event"
        }
        override suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity) = Unit
        override suspend fun acknowledge(uid: String, generation: String) { marked = true; operations += "marker" }
    }
}
