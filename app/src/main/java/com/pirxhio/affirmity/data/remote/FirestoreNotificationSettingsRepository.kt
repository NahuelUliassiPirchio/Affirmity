package com.pirxhio.affirmity.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DaySegment
import com.pirxhio.affirmity.data.local.QuietHoursSettings
import com.pirxhio.affirmity.data.repository.NotificationSettingsRepository
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [NotificationSettingsRepository] backed by the single shared `users/{uid}/settings/preferences`
 * document, both channels prefixed per [NotificationChannelSpec.prefsPrefix] (mirrors
 * `NotificationPreferences`' DataStore key convention). Thin Firebase SDK glue, untested by design.
 */
class FirestoreNotificationSettingsRepository(
    private val firestore: FirebaseFirestore,
    private val uid: String,
) : NotificationSettingsRepository {

    private fun document() = firestore.document(FirestorePaths.settingsPreferencesDoc(uid))

    override fun observe(channel: NotificationChannelSpec): Flow<ChannelSettings> = callbackFlow {
        val registration = document().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(channelSettingsFromMap(channel, snapshot?.data.orEmpty()))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setEnabled(channel: NotificationChannelSpec, enabled: Boolean) {
        // Reuses enabledKey (FirestoreMappers.kt) rather than re-deriving "${channel.prefsPrefix}_enabled"
        // inline -- that duplication had drifted out of sync with the server's
        // meditation_return_enabled field name for MEDITATION_RETURN.
        document().set(mapOf(enabledKey(channel) to enabled), SetOptions.merge()).await()
    }

    override suspend fun setSegments(channel: NotificationChannelSpec, segments: Set<DaySegment>) {
        document().set(
            mapOf(segmentsKey(channel) to segments.map { it.key }),
            SetOptions.merge(),
        ).await()
    }

    override fun observeQuietHours(): Flow<QuietHoursSettings> = callbackFlow {
        val registration = document().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(quietHoursSettingsFromMap(snapshot?.data.orEmpty()))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setQuietHoursEnabled(enabled: Boolean) {
        document().set(mapOf("quietHours_enabled" to enabled), SetOptions.merge()).await()
    }

    override suspend fun setQuietHoursWindow(startMinute: Int, endMinute: Int) {
        document().set(
            mapOf(
                "quietHours_startMinute" to startMinute,
                "quietHours_endMinute" to endMinute,
            ),
            SetOptions.merge(),
        ).await()
    }

    override suspend fun setTimeZone(zoneId: String) {
        document().set(
            mapOf(
                "timeZone" to zoneId,
                "timeZoneUpdatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }
}
