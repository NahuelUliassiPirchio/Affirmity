package com.pirxhio.affirmity.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pirxhio.affirmity.MainActivity
import com.pirxhio.affirmity.R

/**
 * Builds and posts a single channel's notification. Centralizes the
 * [NotificationManagerCompat.areNotificationsEnabled] guard so both workers post the same way —
 * if the user (or the system) has disabled notifications, `notify()` is silently skipped and the
 * chain keeps rescheduling for whenever permission/preference is restored.
 */
class Notifier(private val context: Context) {

    fun notify(channel: NotificationChannelSpec, title: String, body: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            channel.notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channel.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(channel.notificationId, notification)
    }
}
