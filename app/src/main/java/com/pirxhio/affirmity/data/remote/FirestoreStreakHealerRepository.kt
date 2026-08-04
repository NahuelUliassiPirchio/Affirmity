package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import com.pirxhio.affirmity.data.repository.StreakHealerRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [StreakHealerRepository] backed by `users/{uid}/streakHealerUses`. Doc id is
 * `healedEpochDay.toString()` (idempotency, re-activating the same day rewrites the same doc),
 * range reads filter on the numeric `healedEpochDay` field — mirrors
 * [FirestoreDailyMoodRepository]'s doc-id/range-query convention. Thin Firebase SDK glue,
 * untested by design.
 */
class FirestoreStreakHealerRepository(
    private val firestore: FirebaseFirestore,
    private val uid: String,
) : StreakHealerRepository {

    private fun collection() = firestore.collection(FirestorePaths.streakHealerUsesCollection(uid))

    private fun rangeQuery(from: Long, to: Long) = collection()
        .whereGreaterThanOrEqualTo(FIELD_HEALED_EPOCH_DAY, from)
        .whereLessThanOrEqualTo(FIELD_HEALED_EPOCH_DAY, to)

    override fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>> = callbackFlow {
        val registration = rangeQuery(from, to).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().mapNotNull { doc -> doc.data?.let(::streakHealerUseFromMap) })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity> =
        rangeQuery(from, to).get().await().documents.mapNotNull { doc -> doc.data?.let(::streakHealerUseFromMap) }

    override suspend fun recordUse(healedEpochDay: Long) {
        val entity = StreakHealerUseEntity(healedEpochDay = healedEpochDay, activatedAtMillis = System.currentTimeMillis())
        collection().document(healedEpochDay.toString()).set(streakHealerUseToMap(entity)).await()
    }

    private companion object {
        const val FIELD_HEALED_EPOCH_DAY = "healedEpochDay"
    }
}
