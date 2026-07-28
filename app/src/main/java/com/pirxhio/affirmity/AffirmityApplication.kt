package com.pirxhio.affirmity

import android.app.Application
import android.app.NotificationChannel
import androidx.core.app.NotificationManagerCompat
import androidx.work.Configuration
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.data.local.NotificationPreferences
import com.pirxhio.affirmity.notifications.AffirmityWorkerFactory
import com.pirxhio.affirmity.notifications.NotificationChannelSpec

class AffirmityApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                AffirmityWorkerFactory(
                    affirmationDao = AffirmityDatabase.getInstance(this).affirmationDao(),
                    preferences = NotificationPreferences(this),
                )
            )
            .build()

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
