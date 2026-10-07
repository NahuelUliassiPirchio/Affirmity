package com.pirxhio.affirmity.notifications

import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pirxhio.affirmity.EXTRA_NOTIFICATION_ACTIVITY
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
        val activity = attribution.activity
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
                activity?.let { putExtra(EXTRA_NOTIFICATION_ACTIVITY, it) }
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val spec = notificationStyleSpec(channel, title, body, attribution)
        // A fresh builder per attempt so a half-applied custom style never leaks into the fallback.
        fun newBuilder() = NotificationCompat.Builder(context, channel.channelId)
            .setSmallIcon(R.drawable.notification_icon_24dp)
            .setContentTitle(title)
            .setContentText(body)
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

        postWithPlainFallback(
            usesCustomViews = spec.usesCustomViews(),
            postStyled = {
                notificationManager.notify(
                    deliveryNotificationId,
                    newBuilder().applyStyle(spec, contentIntent).build(),
                )
            },
            postPlain = {
                notificationManager.notify(
                    deliveryNotificationId,
                    newBuilder().setStyle(NotificationCompat.BigTextStyle().bigText(body)).build(),
                )
            },
            onFallback = { error ->
                Log.w(TAG, "Custom notification layout failed for ${channel.channelId}; posting plain", error)
            },
        )
        // Reached only after a successful post (styled or plain fallback).
        debugLog.record(channel, NotificationLogEvent.NOTIFY_POSTED)
    }

    /**
     * Applies the per-type look. Streak/reflection/mood use custom RemoteViews inside
     * [NotificationCompat.DecoratedCustomViewStyle] so the system keeps the header (icon, app name,
     * time) and expand affordance; RemoteViews are the only way to get a bespoke layout (count
     * typography) since the built-in styles cannot render one. Everything else keeps
     * the system BigText look. Each card ships its own tinted surface with light/dark colors so
     * contrast never depends on the system shade.
     */
    private fun NotificationCompat.Builder.applyStyle(
        spec: NotificationStyleSpec,
        contentIntent: PendingIntent,
    ): NotificationCompat.Builder = when (spec) {
        is NotificationStyleSpec.Streak -> customViews(
            collapsed = streakViews(R.layout.notification_streak_collapsed, spec, contentIntent),
            expanded = streakViews(R.layout.notification_streak_expanded, spec, contentIntent),
        )
        is NotificationStyleSpec.Reflection -> customViews(
            collapsed = reflectionViews(R.layout.notification_reflection_collapsed, spec, contentIntent),
            expanded = reflectionViews(R.layout.notification_reflection_expanded, spec, contentIntent),
        )
        is NotificationStyleSpec.Mood -> customViews(
            collapsed = moodViews(R.layout.notification_mood_collapsed, spec, contentIntent),
            expanded = moodViews(R.layout.notification_mood_expanded, spec, contentIntent),
        )
        is NotificationStyleSpec.Plain -> setStyle(NotificationCompat.BigTextStyle().bigText(spec.body))
    }

    private fun NotificationCompat.Builder.customViews(
        collapsed: RemoteViews,
        expanded: RemoteViews,
    ): NotificationCompat.Builder = setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(collapsed)
        .setCustomBigContentView(expanded)

    private fun streakViews(@LayoutRes layout: Int, spec: NotificationStyleSpec.Streak, tap: PendingIntent) =
        RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.streak_title, spec.title)
            setTextViewText(R.id.streak_body, spec.body)
            if (spec.count != null) {
                setTextViewText(R.id.streak_count, spec.count.toString())
            } else {
                setViewVisibility(R.id.streak_count, View.GONE)
            }
            // Chip and breakdown rows only exist in the expanded layout. The chip is legacy (old
            // servers); the rows explain which activities hold the overall streak.
            if (layout == R.layout.notification_streak_expanded) {
                bindBreakdownRow(
                    R.id.streak_breakdown_meditation,
                    R.string.notification_streak_activity_meditation,
                    spec.meditationDays,
                )
                bindBreakdownRow(
                    R.id.streak_breakdown_affirmations,
                    R.string.notification_streak_activity_affirmations,
                    spec.affirmationsDays,
                )
                if (spec.activity != null) {
                    setTextViewText(R.id.streak_activity, context.getString(spec.activity.labelRes()))
                    setViewVisibility(R.id.streak_activity, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.streak_activity, View.GONE)
                }
            }
            setOnClickPendingIntent(R.id.streak_root, tap)
        }

    private fun reflectionViews(
        @LayoutRes layout: Int,
        spec: NotificationStyleSpec.Reflection,
        tap: PendingIntent,
    ) = RemoteViews(context.packageName, layout).apply {
        setTextViewText(R.id.reflection_title, spec.title)
        setTextViewText(R.id.reflection_question, spec.body)
        setOnClickPendingIntent(R.id.reflection_root, tap)
    }

    private fun moodViews(@LayoutRes layout: Int, spec: NotificationStyleSpec.Mood, tap: PendingIntent) =
        RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.mood_title, spec.title)
            setTextViewText(R.id.mood_body, spec.body)
            setOnClickPendingIntent(R.id.mood_root, tap)
        }

    private fun RemoteViews.bindBreakdownRow(@IdRes viewId: Int, @StringRes labelRes: Int, days: Int?) {
        val row = breakdownRow(
            days = days,
            label = context.getString(labelRes),
            daysText = { count -> context.resources.getQuantityString(R.plurals.notification_streak_days, count, count) },
            line = { label, daysText -> context.getString(R.string.notification_streak_breakdown_line, label, daysText) },
        )
        when (row) {
            BreakdownRow.Hidden -> setViewVisibility(viewId, View.GONE)
            is BreakdownRow.Visible -> {
                setTextViewText(viewId, row.text)
                setViewVisibility(viewId, View.VISIBLE)
            }
        }
    }

    private fun StreakActivity.labelRes(): Int = when (this) {
        StreakActivity.MEDITATION -> R.string.notification_streak_activity_meditation
        StreakActivity.AFFIRMATIONS -> R.string.notification_streak_activity_affirmations
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
