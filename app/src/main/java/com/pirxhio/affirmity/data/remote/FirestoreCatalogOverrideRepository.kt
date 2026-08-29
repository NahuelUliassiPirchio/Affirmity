package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.pirxhio.affirmity.data.repository.CatalogOverrideRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [CatalogOverrideRepository] backed by `users/{uid}/catalogOverrides` (design D9/D1). Mirrors
 * [FirestoreAffirmationRepository]'s shape: snapshot-listener reads, whole-document writes. An
 * empty override map DELETES the doc rather than persisting `{}`, matching
 * [com.pirxhio.affirmity.data.repository.RoomCatalogOverrideRepository]'s "no overrides has
 * exactly one representation" contract.
 */
class FirestoreCatalogOverrideRepository(
    private val firestore: FirebaseFirestore,
    private val uid: String,
) : CatalogOverrideRepository {

    private fun collection() = firestore.collection(FirestorePaths.catalogOverridesCollection(uid))

    override fun observeAll(): Flow<Map<String, Map<String, String>>> = callbackFlow {
        val registration = collection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val overrides = snapshot?.documents.orEmpty().associate { doc ->
                val map = (doc.get(FIELD_OVERRIDES) as? Map<*, *>)
                    ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.let { key to it } } }
                    ?.toMap()
                    .orEmpty()
                doc.id to map
            }
            trySend(overrides)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setOverrides(catalogAffirmationId: String, overrides: Map<String, String>) {
        val sanitized = overrides.filterValues { it.isNotBlank() }
        val doc = collection().document(catalogAffirmationId)
        if (sanitized.isEmpty()) {
            doc.delete().await()
        } else {
            doc.set(mapOf(FIELD_OVERRIDES to sanitized)).await()
        }
    }
}
