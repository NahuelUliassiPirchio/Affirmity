package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.local.ActivityDao
import com.pirxhio.affirmity.data.local.ActivityEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito

class ActivitySyncTest {
    @Test fun `A to B transition never redirects remaining A payloads`() = runTest {
        val dao = Mockito.mock(ActivityDao::class.java)
        val first = ActivityEventEntity("account:A", "one", 20_000, "guided", 1_000,
            0, 1_000, "UTC", "breathing", "Breathing", 120, "device", "PENDING")
        val pending = mutableListOf(first, first.copy(eventId = "two"))
        Mockito.`when`(dao.pending(Mockito.anyString(), Mockito.anyInt())).thenAnswer { pending.toList() }
        for (id in listOf("one", "two")) {
            Mockito.doAnswer { pending.removeAll { row -> row.eventId == id }; null }
                .`when`(dao).acknowledge("account:A", id)
        }
        var currentUid = "A"
        val accepted = mutableListOf<String>()
        val sync = ActivityEventSync(dao, "A", ActivityUploader { uid, event ->
            accepted += "$uid:${event.eventId}"
            currentUid = "B"
        }, currentUid = { currentUid })
        sync.retryPending()
        sync.retryPending()
        assertEquals(listOf("A:one"), accepted)
        assertEquals(listOf("two"), pending.map { it.eventId })
        currentUid = "A"
        sync.retryPending()
        assertEquals(listOf("A:one", "A:two"), accepted)
        assertEquals(emptyList<ActivityEventEntity>(), pending)
    }

    @Test fun `failed upload retains payload and later retry acknowledges original account once`() = runTest {
        val dao = Mockito.mock(ActivityDao::class.java)
        val event = ActivityEventEntity("account:A", "session", 20_000, "guided", 1_000,
            0, 1_000, "UTC", "breathing", "Breathing", 120, "device", "PENDING")
        val pending = mutableListOf(event)
        Mockito.`when`(dao.pending(Mockito.anyString(), Mockito.anyInt())).thenAnswer { pending.toList() }
        Mockito.doAnswer { pending.clear(); null }.`when`(dao).acknowledge("account:A", "session")
        val accepted = mutableListOf<Pair<String, ActivityEventEntity>>()
        var failUpload = true
        val uploader = ActivityUploader { uid, payload ->
            if (failUpload) error("Offline")
            accepted += uid to payload
        }
        val sync = ActivityEventSync(dao, "A", uploader, currentUid = { "A" })
        try {
            sync.retryPending()
            fail("Offline is a retryable failure")
        } catch (_: IllegalStateException) {
            assertEquals(listOf(event), pending)
        }
        failUpload = false
        sync.retryPending()
        sync.retryPending()
        assertEquals(listOf("A" to event), accepted)
        assertEquals(emptyList<ActivityEventEntity>(), pending)
    }
}
