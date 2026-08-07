package com.pirxhio.affirmity

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.pirxhio.affirmity.notifications.NotificationChannelSpec

class AffirmityApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /** Idempotent: re-creating a channel with the same id only updates name/description.
     * Channels don't exist below API 26 (minSdk 24) -- notifications there just have no channel. */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = NotificationManagerCompat.from(this)
        NotificationChannelSpec.entries.forEach { spec ->
            val channel = NotificationChannel(
                spec.channelId,
                getString(spec.channelNameRes),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(spec.channelDescriptionRes)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
