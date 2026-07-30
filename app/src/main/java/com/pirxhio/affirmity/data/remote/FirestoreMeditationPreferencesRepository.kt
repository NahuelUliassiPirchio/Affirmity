package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pirxhio.affirmity.data.repository.MeditationPreferencesRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [MeditationPreferencesRepository] backed by the single shared `users/{uid}/settings/preferences`
 * document (design.md's File Changes note — same document as the notification settings repo).
 * Thin Firebase SDK glue, untested by design.
 */
class FirestoreMeditationPreferencesRepository(
    private val firestore: FirebaseFirestore,
    private val uid: String,
) : MeditationPreferencesRepository {

    private fun document() = firestore.document(FirestorePaths.settingsPreferencesDoc(uid))

    override fun observeMeditationDurationSeconds(): Flow<Int?> = callbackFlow {
        val registration = document().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.getLong(FIELD_MEDITATION_DURATION_SECONDS)?.toInt())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveMeditationDurationSeconds(seconds: Int) {
        document().set(mapOf(FIELD_MEDITATION_DURATION_SECONDS to seconds), SetOptions.merge()).await()
    }

    private companion object {
        const val FIELD_MEDITATION_DURATION_SECONDS = "meditationDurationSeconds"
    }
}
