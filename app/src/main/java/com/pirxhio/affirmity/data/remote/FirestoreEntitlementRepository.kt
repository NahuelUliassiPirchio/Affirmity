package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.data.RawEntitlementDoc
import com.pirxhio.affirmity.data.repository.Entitlement
import com.pirxhio.affirmity.data.repository.EntitlementRepository
import com.pirxhio.affirmity.data.resolveTier
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [EntitlementRepository] backed by the server-write-only `users/{uid}/entitlements/current`
 * document (design.md D4/D5). Read-only: no write methods exist, so the client can never grant
 * itself Pro. A missing document (never purchased) resolves to [Entitlement.Free], same as
 * [com.pirxhio.affirmity.data.repository.LocalFreeEntitlementRepository]. Snapshots served from
 * Firestore's offline cache (`isFromCache`) are honored unchanged — the required "fail toward
 * last-known state, never a surprise downgrade" behavior for a Pro user who loses connectivity.
 *
 * `callbackFlow` + `addSnapshotListener` + `awaitClose`, same shape as
 * [FirestoreMeditationPreferencesRepository].
 */
class FirestoreEntitlementRepository(
    private val firestore: FirebaseFirestore,
    private val uid: String,
) : EntitlementRepository {

    private fun document() = firestore.document(FirestorePaths.entitlementDoc(uid))

    override fun observe(): Flow<Entitlement> = callbackFlow {
        val registration = document().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val rawTier = snapshot?.getString(FIELD_TIER)
            val doc = if (snapshot == null || !snapshot.exists() || rawTier == null) {
                null
            } else {
                RawEntitlementDoc(
                    tier = rawTier,
                    expiryTimeMillis = snapshot.getLong(FIELD_EXPIRY_TIME_MILLIS),
                )
            }
            val tier = resolveTier(doc, System.currentTimeMillis())
            trySend(
                if (tier == AccessTier.PRO) {
                    Entitlement(
                        tier = AccessTier.PRO,
                        expiryTimeMillis = doc?.expiryTimeMillis,
                        productId = snapshot?.getString(FIELD_PRODUCT_ID),
                    )
                } else {
                    Entitlement.Free
                },
            )
        }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val FIELD_TIER = "tier"
        const val FIELD_EXPIRY_TIME_MILLIS = "expiryTimeMillis"
        const val FIELD_PRODUCT_ID = "productId"
    }
}
