package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pirxhio.affirmity.data.ActivityCoverageInterval
import com.pirxhio.affirmity.data.local.ActivityCoverageEntity
import com.pirxhio.affirmity.data.local.ActivityEventEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Thin SDK adapter; all payload validation and source identity are shared with retry/import reads. */
class FirebaseActivityClient(private val firestore: FirebaseFirestore) : ActivityRemoteClient {
    /** Deterministic doc id and identical payload on every send, so a re-send after an abandoned (timed-out) upload is an identical update. */
    override suspend fun uploadEvent(uid: String, event: ActivityEventEntity) {
        require(event.owner == "account:$uid")
        val payload = activityEventToMap(event)
        activityEventFromMap(event.owner, event.eventId, payload)
        firestore.document(FirestorePaths.activityEventDoc(uid, event.eventId)).set(payload).await()
    }

    override suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity) {
        require(coverage.owner == "account:$uid")
        val id = coverageIdentity(ActivityCoverageInterval(coverage.owner, coverage.sourceId,
            coverage.fromEpochDay, coverage.toEpochDay, coverage.recordingSchema, coverage.uncertain))
        val payload = activityCoverageToMap(coverage)
        activityCoverageFromMap(coverage.owner, id, payload)
        firestore.document(FirestorePaths.activityCoverageDoc(uid, id)).set(payload).await()
    }

    override fun observeEvents(uid: String, from: Long, to: Long) = snapshots(
        firestore.collection(FirestorePaths.activityEventsCollection(uid))
            .whereGreaterThanOrEqualTo("epochDay", from).whereLessThanOrEqualTo("epochDay", to),
    ) { document -> activityEventFromMap("account:$uid", document.id, requireNotNull(document.data)) }

    override fun observeCoverage(uid: String, from: Long, to: Long) = snapshots(
        firestore.collection(FirestorePaths.activityCoverageCollection(uid))
            .whereLessThanOrEqualTo("fromEpochDay", to).whereGreaterThanOrEqualTo("toEpochDay", from)
            .orderBy("fromEpochDay").orderBy("toEpochDay"),
    ) { document -> activityCoverageFromMap("account:$uid", document.id, requireNotNull(document.data)) }

    private fun <T> snapshots(query: Query, decode: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, failure ->
            if (failure != null) {
                close(failure)
                return@addSnapshotListener
            }
            try {
                trySend(requireNotNull(snapshot) { "Activity snapshot unavailable" }.documents.map(decode))
            } catch (failure: Exception) {
                close(failure)
            }
        }
        awaitClose { registration.remove() }
    }
}
