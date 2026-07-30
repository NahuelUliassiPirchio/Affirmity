package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.repository.DailyCompletionRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [DailyCompletionRepository] backed by `users/{uid}/dailyCompletions`. Doc id is
 * `epochDay.toString()` (idempotency), range reads filter on the numeric `epochDay` field
 * (design.md's "Completion doc IDs" decision — lexicographic doc-id ordering breaks for
 * negative/variable-length numbers). `set(..., merge)` reproduces Room's
 * `insertIfAbsent + UPDATE` semantics in one idempotent op. Thin Firebase SDK glue, untested by
 * design.
 */
class FirestoreDailyCompletionRepository(
    private val firestore: FirebaseFirestore,
    private val uid: String,
) : DailyCompletionRepository {

    private fun collection() = firestore.collection(FirestorePaths.dailyCompletionsCollection(uid))

    private fun rangeQuery(from: Long, to: Long) = collection()
        .whereGreaterThanOrEqualTo(FIELD_EPOCH_DAY, from)
        .whereLessThanOrEqualTo(FIELD_EPOCH_DAY, to)

    override fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>> = callbackFlow {
        val registration = rangeQuery(from, to).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().mapNotNull { doc -> doc.data?.let(::dailyCompletionFromMap) })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity> =
        rangeQuery(from, to).get().await().documents.mapNotNull { doc -> doc.data?.let(::dailyCompletionFromMap) }

    override suspend fun markMeditation(epochDay: Long) = markDone(epochDay, FIELD_MEDITATION_DONE)

    override suspend fun markAffirmation(epochDay: Long) = markDone(epochDay, FIELD_AFFIRMATION_DONE)

    private suspend fun markDone(epochDay: Long, doneField: String) {
        collection().document(epochDay.toString())
            .set(mapOf(FIELD_EPOCH_DAY to epochDay, doneField to true), SetOptions.merge())
            .await()
    }

    private companion object {
        const val FIELD_EPOCH_DAY = "epochDay"
        const val FIELD_MEDITATION_DONE = "meditationDone"
        const val FIELD_AFFIRMATION_DONE = "affirmationDone"
    }
}
