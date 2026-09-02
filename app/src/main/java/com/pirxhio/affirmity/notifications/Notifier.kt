package com.pirxhio.affirmity.notifications

import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pirxhio.affirmity.AppDestinations
import com.pirxhio.affirmity.EXTRA_MOOD_VALUE
import com.pirxhio.affirmity.EXTRA_OPEN_MOOD_PICKER
import com.pirxhio.affirmity.EXTRA_START_DESTINATION
import com.pirxhio.affirmity.MainActivity
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.NotificationLogEvent
import com.pirxhio.affirmity.ui.mood.MOOD_VALUES
import com.pirxhio.affirmity.ui.mood.moodEmoji

/**
 * Builds and posts a single channel's notification. Centralizes global-permission and per-channel
 * blocking checks and records the precise skip reason in [NotificationDebugLog].
 */
class Notifier(
    private val context: Context,
    private val debugLog: NotificationDebugLog,
) : NotificationPoster {

    override suspend fun notify(channel: NotificationChannelSpec, title: String, body: String) {
        val notificationManager = NotificationManagerCompat.from(context)
        val channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.getNotificationChannel(channel.channelId)?.importance
        } else {
            null
        }
        val skipEvent = notificationSkipEvent(
            notificationsEnabled = notificationManager.areNotificationsEnabled(),
            sdkInt = Build.VERSION.SDK_INT,
            channelImportance = channelImportance,
        )
        if (skipEvent != null) {
            debugLog.record(channel, skipEvent)
            return
        }

        val deliveryNotificationId = channel.notificationIdForDelivery(System.currentTimeMillis())

        val contentIntent = PendingIntent.getActivity(
            context,
            deliveryNotificationId,
            Intent(context, MainActivity::class.java).apply {
                notificationStartDestination(channel)?.let { destination ->
                    putExtra(EXTRA_START_DESTINATION, destination.name)
                }
                if (notificationOpensMoodPicker(channel)) {
                    putExtra(EXTRA_OPEN_MOOD_PICKER, true)
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
            .apply {
                if (channel.isTimeSensitive) {
                    setPriority(NotificationCompat.PRIORITY_HIGH)
                    setDefaults(NotificationCompat.DEFAULT_ALL)
                }
            }

        // Quick-answer CTA: each button opens the app straight on the mood sheet for today with
        // that value pre-selected, so answering "how was your day" doesn't require navigating there.
        var actionsAdded = 0
        if (channel == NotificationChannelSpec.MOOD) {
            MOOD_QUICK_ACTION_VALUES.forEach { moodValue ->
                val actionIntent = PendingIntent.getActivity(
                    context,
                    deliveryNotificationId * 10 + moodValue,
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(EXTRA_START_DESTINATION, AppDestinations.ANIMO.name)
                        putExtra(EXTRA_MOOD_VALUE, moodValue)
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(R.drawable.notification_icon_24dp, moodEmoji(moodValue), actionIntent)
                actionsAdded++
            }
            Log.d(TAG, "mood notification built with $actionsAdded mood actions (channel=${channel.name})")
        }

        notificationManager.notify(deliveryNotificationId, builder.build())
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

internal val MOOD_QUICK_ACTION_VALUES: List<Int> = MOOD_VALUES.filter { it in 2..4 }

internal fun notificationStartDestination(channel: NotificationChannelSpec): AppDestinations? = when (channel) {
    NotificationChannelSpec.MOOD -> AppDestinations.ANIMO
    NotificationChannelSpec.STREAK, NotificationChannelSpec.HEALER -> AppDestinations.PROGRESO
    NotificationChannelSpec.REMINDER, NotificationChannelSpec.REFLECTION -> null
}

internal fun notificationOpensMoodPicker(channel: NotificationChannelSpec): Boolean =
    channel == NotificationChannelSpec.MOOD

internal fun isNotificationChannelBlocked(sdkInt: Int, importance: Int?): Boolean =
    sdkInt >= Build.VERSION_CODES.O && importance == NotificationManager.IMPORTANCE_NONE

internal fun notificationSkipEvent(
    notificationsEnabled: Boolean,
    sdkInt: Int,
    channelImportance: Int?,
): NotificationLogEvent? = when {
    !notificationsEnabled -> NotificationLogEvent.NOTIFY_SKIPPED_PERMISSION
    isNotificationChannelBlocked(sdkInt, channelImportance) ->
        NotificationLogEvent.NOTIFY_SKIPPED_CHANNEL_BLOCKED
    else -> null
}
