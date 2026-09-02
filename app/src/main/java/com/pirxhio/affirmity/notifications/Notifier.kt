package com.pirxhio.affirmity.notifications

import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pirxhio.affirmity.EXTRA_NOTIFICATION_DESTINATION
import com.pirxhio.affirmity.EXTRA_NOTIFICATION_FAMILY
import com.pirxhio.affirmity.EXTRA_NOTIFICATION_LOCALE
import com.pirxhio.affirmity.EXTRA_NOTIFICATION_QUESTION_ID
import com.pirxhio.affirmity.EXTRA_NOTIFICATION_QUESTION_TEXT
import com.pirxhio.affirmity.EXTRA_NOTIFICATION_VARIANT_KEY
import com.pirxhio.affirmity.EXTRA_OPEN_MOOD_PICKER
import com.pirxhio.affirmity.EXTRA_START_DESTINATION
import com.pirxhio.affirmity.AppDestinations
import com.pirxhio.affirmity.MainActivity
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.NotificationLogEvent

/**
 * Builds and posts a single channel's notification. Centralizes global-permission and per-channel
 * blocking checks and records the precise skip reason in [NotificationDebugLog].
 */
class Notifier(
    private val context: Context,
    private val debugLog: NotificationDebugLog,
) : NotificationPoster {

    override suspend fun notify(
        channel: NotificationChannelSpec,
        title: String,
        body: String,
        attribution: NotificationAttribution,
    ) {
        val (destination, expiringToday, questionId, family, variantKey, locale) = attribution
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

        // Cancel-then-post (Notifications V2 design §6): cancel every currently-active
        // notification in this channel's delivery-ID namespace before posting the new one, so at
        // most one notification per channel is ever visible. The rotating-ID pool itself (#1224/
        // #1237 collision safety) is untouched — this only cancels, never reuses, those ids.
        // Resilience fix: getActiveNotifications() is a known OEM-flaky API (throws on some
        // Samsung/Xiaomi skins) -- a throw here must never stop the notification from posting.
        val activeIds = activeNotificationIdsOrEmpty(TAG) { notificationManager.activeNotifications.map { it.id } }
        idsToCancelBeforePost(channel, activeIds).forEach { idToCancel ->
            notificationManager.cancel(idToCancel)
        }

        val deliveryNotificationId = channel.notificationIdForDelivery(System.currentTimeMillis())

        val resolvedDestination = destination?.let { raw ->
            AppDestinations.entries.find { it.name == raw }
        } ?: notificationStartDestination(channel)

        val contentIntent = PendingIntent.getActivity(
            context,
            deliveryNotificationId,
            Intent(context, MainActivity::class.java).apply {
                resolvedDestination?.let { putExtra(EXTRA_START_DESTINATION, it.name) }
                if (notificationOpensMoodPicker(channel)) {
                    putExtra(EXTRA_OPEN_MOOD_PICKER, true)
                }
                // `body` doubles as the question's display text for the Compass answer screen
                // (Notifications V2 scope-expansion decision): the copy catalog is Admin-SDK-only,
                // so the client never looks the question text up itself -- the notification's own
                // rendered body IS the question, verbatim.
                questionId?.let {
                    putExtra(EXTRA_NOTIFICATION_QUESTION_ID, it)
                    putExtra(EXTRA_NOTIFICATION_QUESTION_TEXT, body)
                }
                // Notifications V2 analytics (design §9): attached to every posted notification's
                // PendingIntent so notification_opened (and, once a CTA action exists,
                // notification_action_clicked) can attribute the exact family/variant/locale a tap
                // resolves back to -- resolved back out by MainActivity's resolveNotification*
                // functions. `destination` here is the raw wire token (e.g. "mood_checkin"), kept
                // distinct from EXTRA_START_DESTINATION's already-resolved AppDestinations name.
                family?.let { putExtra(EXTRA_NOTIFICATION_FAMILY, it) }
                variantKey?.let { putExtra(EXTRA_NOTIFICATION_VARIANT_KEY, it) }
                destination?.let { putExtra(EXTRA_NOTIFICATION_DESTINATION, it) }
                locale?.let { putExtra(EXTRA_NOTIFICATION_LOCALE, it) }
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
                // Healer sits at DEFAULT importance normally and only escalates when today's
                // window closes (design §5's "conditional HIGH", effective below API 26).
                if (channel.importance == ChannelImportance.HIGH ||
                    (channel == NotificationChannelSpec.HEALER && expiringToday)
                ) {
                    setPriority(NotificationCompat.PRIORITY_HIGH)
                    setDefaults(NotificationCompat.DEFAULT_ALL)
                }
            }

        notificationManager.notify(deliveryNotificationId, builder.build())
        debugLog.record(channel, NotificationLogEvent.NOTIFY_POSTED)
    }

    private companion object {
        const val TAG = "Notifier"
    }
}

/** Wraps [android.app.NotificationManager.getActiveNotifications] (surfaced here via
 * [fetchActiveIds], typically `NotificationManagerCompat.activeNotifications`), a known OEM-flaky
 * API that throws `SecurityException`/`NullPointerException`/other `RuntimeException`s on some
 * Samsung/Xiaomi/other device skins. On failure, logs and returns an empty list so the caller
 * falls through to posting/cancelling normally, un-deduped, rather than not posting at all -- a
 * stacked notification is a much better failure mode than a silently dropped one. Pure seam
 * (mirrors [idsToCancelBeforePost]/[notificationSkipEvent]'s testing convention), used by both
 * [Notifier] and [NotificationCanceller]. */
internal fun activeNotificationIdsOrEmpty(tag: String, fetchActiveIds: () -> List<Int>): List<Int> =
    try {
        fetchActiveIds()
    } catch (error: Exception) {
        Log.w(tag, "getActiveNotifications threw; skipping cancel-then-post dedup for this call", error)
        emptyList()
    }

internal fun notificationStartDestination(channel: NotificationChannelSpec): AppDestinations? = when (channel) {
    NotificationChannelSpec.MOOD -> AppDestinations.ANIMO
    NotificationChannelSpec.STREAK, NotificationChannelSpec.HEALER -> AppDestinations.PROGRESO
    NotificationChannelSpec.REMINDER, NotificationChannelSpec.REFLECTION,
    NotificationChannelSpec.MEDITATION_RETURN,
    -> null
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
