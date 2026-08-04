package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.pirxhio.affirmity.data.local.DailyMoodEntity
import com.pirxhio.affirmity.data.repository.DailyMoodRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [DailyMoodRepository] backed by `users/{uid}/dailyMoods`. Doc id is `epochDay.toString()`
 * (idempotency), range reads filter on the numeric `epochDay` field — mirrors
 * [FirestoreDailyCompletionRepository]'s doc-id/range-query convention. A save is a full
 * `set(...)` (no merge) since a mood check-in always writes the whole day's record. Thin Firebase
 * SDK glue, untested by design.
 */
class FirestoreDailyMoodRepository(
    private val firestore: FirebaseFirestore,
    private val uid: String,
) : DailyMoodRepository {

    private fun collection() = firestore.collection(FirestorePaths.dailyMoodsCollection(uid))

    private fun rangeQuery(from: Long, to: Long) = collection()
        .whereGreaterThanOrEqualTo(FIELD_EPOCH_DAY, from)
        .whereLessThanOrEqualTo(FIELD_EPOCH_DAY, to)

    override fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>> = callbackFlow {
        val registration = rangeQuery(from, to).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().mapNotNull { doc -> doc.data?.let(::dailyMoodFromMap) })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity> =
        rangeQuery(from, to).get().await().documents.mapNotNull { doc -> doc.data?.let(::dailyMoodFromMap) }

    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) {
        val entity = DailyMoodEntity(epochDay = epochDay, moodValue = moodValue, note = note)
        collection().document(epochDay.toString()).set(dailyMoodToMap(entity)).await()
    }

    private companion object {
        const val FIELD_EPOCH_DAY = "epochDay"
    }
}
