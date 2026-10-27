package com.pirxhio.affirmity.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.pirxhio.affirmity.data.local.ActivityEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class FirebaseActivityClientTest {
    private val event = ActivityEventEntity("account:A", "session", 20_000, "guided", 1_000,
        0, 1_000, "UTC", "breathing", "Breathing", 120, "device", "PENDING")

    @Test fun `Firebase upload uses captured UID path and immutable event payload`() = runTest {
        val firestore = Mockito.mock(FirebaseFirestore::class.java)
        val document = Mockito.mock(DocumentReference::class.java)
        val expected = activityEventToMap(event)
        Mockito.`when`(firestore.document("users/A/activityEvents/session")).thenReturn(document)
        var saved: Any? = null
        Mockito.`when`(document.set(expected)).thenAnswer {
            saved = it.getArgument<Any>(0)
            Tasks.forResult<Void>(null)
        }
        FirebaseActivityClient(firestore).uploadEvent("A", event)
        assertEquals(expected, saved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Firebase upload cannot redirect A event into B path`() = runTest {
        FirebaseActivityClient(Mockito.mock(FirebaseFirestore::class.java)).uploadEvent("B", event)
        Unit
    }
}
