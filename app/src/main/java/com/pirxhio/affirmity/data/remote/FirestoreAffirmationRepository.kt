package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.repository.AffirmationRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [AffirmationRepository] backed by `users/{uid}/affirmations`. Snapshot-listener reads,
 * `set(..., merge)` writes (design.md's "Completion writes" decision generalized to every
 * collection for retry-safety). Thin Firebase SDK glue, untested by design (matches
 * `FirebaseAuthRepository`'s convention — pure logic lives in `FirestoreMappers`/`FirestorePaths`).
 */
class FirestoreAffirmationRepository(
    private val firestore: FirebaseFirestore,
    private val uid: String,
) : AffirmationRepository {

    private fun collection() = firestore.collection(FirestorePaths.affirmationsCollection(uid))

    override fun observeAll(): Flow<List<AffirmationEntity>> = callbackFlow {
        val registration = collection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().mapNotNull { doc -> doc.data?.let(::affirmationFromMap) })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun insert(entity: AffirmationEntity) {
        collection().document(entity.id)
            .set(affirmationToMap(entity), SetOptions.mergeFields(*AFFIRMATION_FIELDS))
            .await()
    }

    override suspend fun deleteById(id: String) {
        collection().document(id).delete().await()
    }

    override suspend fun deleteAll() {
        val documents = collection().get().await().documents
        val batch = firestore.batch()
        documents.forEach { doc -> batch.delete(doc.reference) }
        batch.commit().await()
    }

    override suspend fun setOverrides(id: String, overrides: Map<String, String>) {
        collection().document(id)
            .set(overridesWritePayload(overrides), SetOptions.mergeFields(FIELD_OVERRIDES))
            .await()
    }
}
