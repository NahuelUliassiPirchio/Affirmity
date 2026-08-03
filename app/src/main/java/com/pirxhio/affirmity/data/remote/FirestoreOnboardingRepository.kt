package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Per-account "has this user already finished onboarding" marker under `users/{uid}/meta/onboarded`
 * (same marker-doc convention as [FirestoreMigrator]'s migration marker). Lets the onboarding
 * flow's "I already have an account" shortcut recognize a returning account from a fresh install
 * and skip the question steps, even though completion is otherwise tracked per-device.
 */
class FirestoreOnboardingRepository(private val firestore: FirebaseFirestore) {

    suspend fun hasCompleted(uid: String): Boolean =
        firestore.document(FirestorePaths.onboardingMarkerDoc(uid)).get().await().exists()

    suspend fun markCompleted(uid: String) {
        firestore.document(FirestorePaths.onboardingMarkerDoc(uid))
            .set(mapOf("completedAt" to FieldValue.serverTimestamp()))
            .await()
    }
}
