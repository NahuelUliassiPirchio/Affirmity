package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.repository.*
import java.util.TimeZone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ActivityCaptureTest {
    @Test fun `views and repeated short completions retain stable identities and captured owner`() = runTest {
        val events = mutableListOf<ActivityEvent>()
        val repository = object : ActivityRepository {
            override suspend fun record(command: ActivityEvent): ActivitySaveState { events += command; return ActivitySaveState.LOCAL }
            override fun observeRange(owner: String, fromEpochDay: Long, toEpochDay: Long) = error("Unused")
            override suspend fun retryPending(owner: String) = Unit
        }
        val binding = ActivitySessionBinding(ActivityIdentityHandle { ActivityIdentity("local:G", "device") }, repository)
        val recorder = ActivityRecorder(binding)
        recorder.view(1_000, TimeZone.getTimeZone("UTC"))
        recorder.view(1_001, TimeZone.getTimeZone("UTC"))
        val first = recorder.startSession(ActivityKind.GUIDED, "breathing", "Name at Start", 2_000)
        val abandoned = recorder.startSession(ActivityKind.FREE, null, "Free timer", 3_000)
        first.complete(1, 4_000, TimeZone.getTimeZone("UTC"))
        first.complete(1, 4_000, TimeZone.getTimeZone("UTC"))
        val second = recorder.startSession(ActivityKind.GUIDED, "breathing", "Name at Start", 5_000)
        second.complete(2, 7_000, TimeZone.getTimeZone("UTC"))
        assertEquals(5, events.size)
        assertEquals(4, events.map { it.eventId }.distinct().size)
        assertEquals(3L, aggregateActivityDay(events.first().epochDay, events, ActivityCoverage.UNKNOWN).activeSeconds)
        assertTrue(events.all { it.owner == "local:G" && it.zoneId == "UTC" })
        assertTrue(events.none { it.kind == ActivityKind.FREE })
    }
}
