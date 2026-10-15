package com.pirxhio.affirmity.widget.carousel

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.remote.FirestoreAffirmationRepository
import com.pirxhio.affirmity.data.remote.OwnedSnapshot
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

class OwnedSnapshotTest {

    private val row = AffirmationEntity(id = "o1", title = "mine", subtitle = "", backgroundType = "color", backgroundValue = "")

    @Test
    fun `an empty cache-only emission is skipped in favour of the server one`() = runTest {
        val rows = flowOf(OwnedSnapshot(emptyList(), fromCache = true), OwnedSnapshot(listOf(row), fromCache = false))
            .firstTrustworthyOwned()

        assertEquals(listOf(row), rows)
    }

    @Test
    fun `an empty server emission is a genuinely empty collection`() = runTest {
        val rows = flowOf(OwnedSnapshot(emptyList(), fromCache = false)).firstTrustworthyOwned()

        assertEquals(emptyList<AffirmationEntity>(), rows)
    }

    @Test
    fun `a non-empty cache emission is usable offline`() = runTest {
        val rows = flowOf(OwnedSnapshot(listOf(row), fromCache = true)).firstTrustworthyOwned()

        assertEquals(listOf(row), rows)
    }

    @Test
    fun `offline with only an empty cache never resolves so the read times out as unavailable`() = runTest {
        val read = readOwned(4_000) {
            flow {
                emit(OwnedSnapshot(emptyList(), fromCache = true))
                awaitCancellation()
            }.firstTrustworthyOwned().map { it.id }
        }

        assertEquals(OwnedRead.Unavailable, read)
    }

    @Test
    fun `an empty server emission after an empty cache one is a genuinely empty collection`() = runTest {
        val rows = flowOf(OwnedSnapshot(emptyList(), fromCache = true), OwnedSnapshot(emptyList(), fromCache = false))
            .firstTrustworthyOwned()

        assertEquals(emptyList<AffirmationEntity>(), rows)
    }

    /**
     * Firestore only re-delivers a snapshot whose documents did not change (an empty cache result
     * later confirmed empty by the server) to listeners registered with [MetadataChanges.INCLUDE].
     */
    @Test
    fun `the widget's owned read sees the server confirming an empty collection`() = runTest {
        val read = readOwned(4_000) {
            FirestoreAffirmationRepository(firestoreDeliveringEmptyCacheThenEmptyServer(), "uid")
                .observeAllWithSource()
                .firstTrustworthyOwned()
                .map { it.id }
        }

        assertEquals(OwnedRead.Available(emptyList()), read)
    }

    private fun emptySnapshot(fromCache: Boolean): QuerySnapshot {
        val metadata = mock<SnapshotMetadata> { on { isFromCache } doReturn fromCache }
        return mock { on { documents } doReturn emptyList(); on { this.metadata } doReturn metadata }
    }

    private fun firestoreDeliveringEmptyCacheThenEmptyServer(): FirebaseFirestore {
        val registration = mock<ListenerRegistration>()
        val collection = mock<CollectionReference> {
            // Without metadata changes the server confirmation differs only in metadata: not delivered.
            on { addSnapshotListener(any<EventListener<QuerySnapshot>>()) } doAnswer { call ->
                call.getArgument<EventListener<QuerySnapshot>>(0).onEvent(emptySnapshot(fromCache = true), null)
                registration
            }
            on { addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>()) } doAnswer { call ->
                val listener = call.getArgument<EventListener<QuerySnapshot>>(1)
                listener.onEvent(emptySnapshot(fromCache = true), null)
                listener.onEvent(emptySnapshot(fromCache = false), null)
                registration
            }
        }
        return mock { on { collection(anyOrNull()) } doReturn collection }
    }
}
