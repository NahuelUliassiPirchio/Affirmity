package com.pirxhio.affirmity.notifications

import androidx.annotation.StringRes
import com.pirxhio.affirmity.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * One notification channel: its Android [android.app.NotificationChannel] identity and the
 * DataStore/Firestore key prefix used by
 * [com.pirxhio.affirmity.data.local.NotificationPreferences] /
 * [com.pirxhio.affirmity.data.remote.FirestoreNotificationSettingsRepository]. Trigger computation
 * is server-driven (see `push-notifications` spec) — this type carries no WorkManager identity.
 */
enum class NotificationChannelSpec(
    val channelId: String,
    val notificationId: Int,
    val prefsPrefix: String,
    val isTimeSensitive: Boolean = false,
    @StringRes val channelNameRes: Int,
    @StringRes val channelDescriptionRes: Int,
) {
    REMINDER(
        channelId = "affirmity_reminders",
        notificationId = 1001,
        prefsPrefix = "reminder",
        channelNameRes = R.string.notification_channel_reminder_name,
        channelDescriptionRes = R.string.notification_channel_reminder_description,
    ),
    REFLECTION(
        channelId = "affirmity_reflection_prompts",
        notificationId = 1002,
        prefsPrefix = "reflection",
        channelNameRes = R.string.notification_channel_reflection_name,
        channelDescriptionRes = R.string.notification_channel_reflection_description,
    ),
    MOOD(
        channelId = "affirmity_mood_checkin",
        notificationId = 1005,
        prefsPrefix = "mood",
        isTimeSensitive = true,
        channelNameRes = R.string.notification_channel_mood_name,
        channelDescriptionRes = R.string.notification_channel_mood_description,
    ),
    STREAK(
        channelId = "affirmity_streak_alerts",
        notificationId = 1003,
        prefsPrefix = "streak",
        isTimeSensitive = true,
        channelNameRes = R.string.notification_channel_streak_name,
        channelDescriptionRes = R.string.notification_channel_streak_description,
    ),
    HEALER(
        channelId = "affirmity_healer_alerts",
        notificationId = 1004,
        prefsPrefix = "healer",
        channelNameRes = R.string.notification_channel_healer_name,
        channelDescriptionRes = R.string.notification_channel_healer_description,
    ),
}

/** Reflection prompts can legitimately arrive more than once per day; the remaining channels are
 * single-instance nudges and keep replacing their own previous delivery. */
fun NotificationChannelSpec.notificationIdForDelivery(@Suppress("UNUSED_PARAMETER") deliveryTimeMillis: Long): Int =
    if (this == NotificationChannelSpec.REFLECTION) {
        notificationId * DELIVERY_ID_NAMESPACE_SIZE +
            reflectionDeliverySequence.getAndUpdate { current ->
                if (current == DELIVERY_ID_NAMESPACE_SIZE - 1) 0 else current + 1
            }
    } else {
        notificationId
    }

private const val DELIVERY_ID_NAMESPACE_SIZE = 1_000_000
private val reflectionDeliverySequence = AtomicInteger(
    Math.floorMod(System.nanoTime().hashCode(), DELIVERY_ID_NAMESPACE_SIZE),
)
