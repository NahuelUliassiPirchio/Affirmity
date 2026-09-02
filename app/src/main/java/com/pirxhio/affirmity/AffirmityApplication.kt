package com.pirxhio.affirmity

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.pirxhio.affirmity.notifications.NotificationChannelSpec

class AffirmityApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
                if (spec.isTimeSensitive) {
                    NotificationManager.IMPORTANCE_HIGH
                } else {
                    NotificationManager.IMPORTANCE_DEFAULT
                },
            ).apply {
                description = getString(spec.channelDescriptionRes)
                if (spec.isTimeSensitive) {
                    enableVibration(true)
                    setSound(defaultSound, null)
                }
            }
            manager.createNotificationChannel(channel)
        }
    }
}
