package com.pirxhio.affirmity.notifications

import androidx.annotation.StringRes
import com.pirxhio.affirmity.R

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
        channelNameRes = R.string.notification_channel_mood_name,
        channelDescriptionRes = R.string.notification_channel_mood_description,
    ),
    STREAK(
        channelId = "affirmity_streak_alerts",
        notificationId = 1003,
        prefsPrefix = "streak",
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
