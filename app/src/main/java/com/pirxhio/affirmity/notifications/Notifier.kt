package com.pirxhio.affirmity.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pirxhio.affirmity.AppDestinations
import com.pirxhio.affirmity.EXTRA_MOOD_VALUE
import com.pirxhio.affirmity.EXTRA_START_DESTINATION
import com.pirxhio.affirmity.MainActivity
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.NotificationLogEvent
import com.pirxhio.affirmity.ui.mood.MOOD_VALUES
import com.pirxhio.affirmity.ui.mood.moodEmoji

/**
 * Builds and posts a single channel's notification. Centralizes the
 * [NotificationManagerCompat.areNotificationsEnabled] guard so both workers post the same way —
 * if the user (or the system) has disabled notifications, `notify()` is silently skipped and the
 * chain keeps rescheduling for whenever permission/preference is restored.
 */
class Notifier(
    private val context: Context,
    private val debugLog: NotificationDebugLog,
) : NotificationPoster {

    override suspend fun notify(channel: NotificationChannelSpec, title: String, body: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            debugLog.record(channel, NotificationLogEvent.NOTIFY_SKIPPED_PERMISSION)
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            channel.notificationId,
            Intent(context, MainActivity::class.java).apply {
                if (channel == NotificationChannelSpec.MOOD) {
                    putExtra(EXTRA_START_DESTINATION, AppDestinations.ANIMO.name)
                }
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channel.channelId)
            .setSmallIcon(R.drawable.notification_icon_24dp)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        // Quick-answer CTA: each button opens the app straight on the mood sheet for today with
        // that value pre-selected, so answering "how was your day" doesn't require navigating there.
        var actionsAdded = 0
        if (channel == NotificationChannelSpec.MOOD) {
            MOOD_VALUES.forEach { moodValue ->
                val actionIntent = PendingIntent.getActivity(
                    context,
                    channel.notificationId * 10 + moodValue,
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(EXTRA_START_DESTINATION, AppDestinations.ANIMO.name)
                        putExtra(EXTRA_MOOD_VALUE, moodValue)
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(R.drawable.notification_icon_24dp, moodEmoji(moodValue), actionIntent)
                actionsAdded++
            }
            // The OS/launcher — not this code — decides how many of these actually render (stock
            // Android caps at 3 inline actions; some OEM shades show fewer depending on width), so
            // logging what we *built* is how a "menos botones de los que esperaba" report gets told
            // apart from an actual bug here.
            Log.d(TAG, "mood notification built with $actionsAdded mood actions (channel=${channel.name})")
        }

        NotificationManagerCompat.from(context).notify(channel.notificationId, builder.build())
        debugLog.record(
            channel,
            NotificationLogEvent.NOTIFY_POSTED,
            detail = if (channel == NotificationChannelSpec.MOOD) "acciones agregadas: $actionsAdded" else "",
        )
    }

    private companion object {
        const val TAG = "Notifier"
    }
}
