package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.pirxhio.affirmity.access.AdUnlockRecord
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.data.repository.AdUnlockRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [AdUnlockRepository] backed by the client-writable `users/{uid}/adUnlocks` collection
 * (design §4a/§8). Unlike [FirestoreEntitlementRepository] this repository DOES write: the
 * client is what observes a rewarded-ad completion, so there is no server signal to write from.
 *
 * `callbackFlow` + `addSnapshotListener` + `awaitClose`, same shape as
 * [FirestoreEntitlementRepository]. [grantDurableUnlock] uses `set()` and treats
 * `PERMISSION_DENIED` as success: the rules (design §8) deny `update`, so a denied duplicate
 * write for an already-granted key IS the "already granted" answer, not a failure.
 */
class FirestoreAdUnlockRepository(
    private val firestore: FirebaseFirestore,
    private val uid: String,
) : AdUnlockRepository {

    private fun collection() = firestore.collection(FirestorePaths.adUnlocksCollection(uid))

    private fun timedCollection() = firestore.collection(FirestorePaths.timedUnlocksCollection(uid))

    override fun observeDurableUnlocks(): Flow<List<AdUnlockRecord>> = callbackFlow {
        val registration = collection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val records = snapshot?.documents.orEmpty().mapNotNull { doc -> doc.data?.let(::recordFromMap) }
            trySend(records)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun getDurableUnlocks(): List<AdUnlockRecord> =
        collection().get().await().documents.mapNotNull { doc -> doc.data?.let(::recordFromMap) }

    override suspend fun grantDurableUnlock(record: AdUnlockRecord) {
        try {
            collection().document(record.key.storageKey).set(recordToMap(record)).await()
        } catch (denied: FirebaseFirestoreException) {
            if (denied.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw denied
            }
        }
    }

    override fun observeTimedUnlocks(): Flow<List<AdUnlockRecord>> = callbackFlow {
        val registration = timedCollection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val records = snapshot?.documents.orEmpty().mapNotNull { doc -> doc.data?.let(::recordFromMap) }
            trySend(records)
        }
        awaitClose { registration.remove() }
    }

    /** UPSERT, unlike [grantDurableUnlock]: the rules (design D16) allow `create` AND `update` on
     *  this collection, so no `PERMISSION_DENIED`-swallow is needed here -- an overwrite for an
     *  existing key IS the re-earn-after-expiry write, not a denied duplicate. */
    override suspend fun grantTimedUnlock(record: AdUnlockRecord) {
        timedCollection().document(record.key.storageKey).set(recordToMap(record)).await()
    }

    private fun recordFromMap(data: Map<String, Any?>): AdUnlockRecord? {
        val contentType = data[FIELD_CONTENT_TYPE] as? String ?: return null
        val contentId = data[FIELD_CONTENT_ID] as? String ?: return null
        val type = ContentType.fromWireName(contentType) ?: return null
        val grantedAtMillis = (data[FIELD_GRANTED_AT_MILLIS] as? Number)?.toLong() ?: return null
        return AdUnlockRecord(
            key = ContentKey(type, contentId),
            grantedAtMillis = grantedAtMillis,
            expiresAtMillis = (data[FIELD_EXPIRES_AT_MILLIS] as? Number)?.toLong(),
        )
    }

    private fun recordToMap(record: AdUnlockRecord): Map<String, Any?> = mapOf(
        FIELD_CONTENT_TYPE to record.key.type.wireName,
        FIELD_CONTENT_ID to record.key.id,
        FIELD_GRANTED_AT_MILLIS to record.grantedAtMillis,
        FIELD_EXPIRES_AT_MILLIS to record.expiresAtMillis,
    )

    private companion object {
        const val FIELD_CONTENT_TYPE = "contentType"
        const val FIELD_CONTENT_ID = "contentId"
        const val FIELD_GRANTED_AT_MILLIS = "grantedAtMillis"
        const val FIELD_EXPIRES_AT_MILLIS = "expiresAtMillis"
    }
}
