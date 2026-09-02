package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Writes/refreshes this device's FCM token under `users/{uid}/fcmTokens/{token}` (design.md's
 * "Token store" decision — doc-id-is-token, multi-device support, prune is a single delete).
 * Signed-in only; there is no signed-out equivalent since signed-out users receive no
 * server-driven notifications (proposal's "Signed-out users" position). Thin Firebase SDK glue,
 * untested by design — matches `FirestoreNotificationSettingsRepository`'s convention.
 */
class FcmTokenRepository(
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
) {

    suspend fun registerToken(uid: String, token: String, onFirebaseSuccess: () -> Unit = {}) {
        val task = firestore.document(FirestorePaths.fcmTokenDoc(uid, token)).set(
            mapOf(
                "token" to token,
                "platform" to "android",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        )
        // Task.await() is cancellable, but cancelling it does not cancel the Firestore write.
        // Keep observing the SDK Task so ownership coordination can clean up a late success.
        task.addOnSuccessListener { onFirebaseSuccess() }
        task.await()
    }

    suspend fun deleteCurrentToken(uid: String) {
        val token = messaging.token.await()
        deleteToken(uid, token)
    }

    suspend fun deleteToken(uid: String, token: String) {
        firestore.document(FirestorePaths.fcmTokenDoc(uid, token)).delete().await()
    }
}
