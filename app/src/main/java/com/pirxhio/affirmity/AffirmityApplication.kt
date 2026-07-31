package com.pirxhio.affirmity

import android.app.Application
import android.app.NotificationChannel
import androidx.core.app.NotificationManagerCompat
import com.pirxhio.affirmity.notifications.NotificationChannelSpec

class AffirmityApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /** Idempotent: re-creating a channel with the same id only updates name/description. */
    private fun createNotificationChannels() {
        val manager = NotificationManagerCompat.from(this)
        NotificationChannelSpec.entries.forEach { spec ->
            val channel = NotificationChannel(
                spec.channelId,
                getString(spec.channelNameRes),
                NotificationManagerCompat.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(spec.channelDescriptionRes)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
