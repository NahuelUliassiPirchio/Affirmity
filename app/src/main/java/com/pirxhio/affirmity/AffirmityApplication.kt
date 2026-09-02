package com.pirxhio.affirmity

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.pirxhio.affirmity.notifications.ChannelImportance
import com.pirxhio.affirmity.notifications.NotificationChannelSpec

class AffirmityApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        deleteRetiredNotificationChannels()
    }

    /** Idempotent: re-creating a channel with the same id only updates name/description. Existing
     * installs keep their current importance/sound/vibration because channel IDs are deliberately
     * not bumped; channels don't exist below API 26 (minSdk 24). */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = NotificationManagerCompat.from(this)
        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        NotificationChannelSpec.entries.forEach { spec ->
            val channel = NotificationChannel(
                spec.channelId,
                getString(spec.channelNameRes),
                platformImportance(spec.importance),
            ).apply {
                description = getString(spec.channelDescriptionRes)
                if (spec.importance == ChannelImportance.HIGH) {
                    enableVibration(true)
                    setSound(defaultSound, null)
                }
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Notifications V2 (design §5) retired the pre-migration Mood channel in favor of
     * `affirmity_mood_checkin_v2`. Android restores a deleted channel's old user-customized
     * settings verbatim if the same ID is ever re-created, so [RETIRED_CHANNEL_IDS] must never
     * again appear in [NotificationChannelSpec]. Deleting an already-deleted (or never-created)
     * channel id is a documented no-op, so this call is safe to repeat on every app start —
     * it does not need its own "ran once" guard beyond that.
     */
    private fun deleteRetiredNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = NotificationManagerCompat.from(this)
        RETIRED_CHANNEL_IDS.forEach { retiredChannelId ->
            manager.deleteNotificationChannel(retiredChannelId)
        }
    }

    internal companion object {
        /** Channel IDs retired by past migrations. Never reuse one of these IDs for a new or
         * renamed channel — see [deleteRetiredNotificationChannels]'s kdoc. `internal` (not
         * `private`) so [AffirmityApplicationTest] can pin this list directly rather than
         * duplicating the literal id, and assert no [NotificationChannelSpec] entry reuses it. */
        val RETIRED_CHANNEL_IDS = listOf("affirmity_mood_checkin")
    }
}

/** Pure `ChannelImportance` -> platform `NotificationManager.IMPORTANCE_*` mapping -- the exact
 * expression [AffirmityApplication.createNotificationChannels] evaluates per channel. Extracted
 * (no Android `Context`/`NotificationManager` dependency) so it's directly JUnit-testable: this
 * repo has no Robolectric and no androidTest convention for `NotificationManager`-touching code
 * (see [AffirmityApplicationTest]'s kdoc for the full testability note), so this pure seam is the
 * most rigorous proof of the importance mapping the existing test infra supports. */
internal fun platformImportance(importance: ChannelImportance): Int = when (importance) {
    ChannelImportance.LOW -> NotificationManager.IMPORTANCE_LOW
    ChannelImportance.DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
    ChannelImportance.HIGH -> NotificationManager.IMPORTANCE_HIGH
}
