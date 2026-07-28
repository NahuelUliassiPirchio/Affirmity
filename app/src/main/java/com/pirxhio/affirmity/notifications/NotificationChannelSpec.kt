package com.pirxhio.affirmity.notifications

import androidx.annotation.StringRes
import com.pirxhio.affirmity.R

/**
 * One notification channel: its Android [android.app.NotificationChannel] identity, the unique
 * WorkManager chain that drives it, and the DataStore key prefix used by
 * [com.pirxhio.affirmity.data.local.NotificationPreferences].
 */
enum class NotificationChannelSpec(
    val channelId: String,
    val notificationId: Int,
    val uniqueWorkName: String,
    val workTag: String,
    val prefsPrefix: String,
    @StringRes val channelNameRes: Int,
    @StringRes val channelDescriptionRes: Int,
) {
    REMINDER(
        channelId = "affirmity_reminders",
        notificationId = 1001,
        uniqueWorkName = "affirmity_reminder_chain",
        workTag = "affirmity_reminder",
        prefsPrefix = "reminder",
        channelNameRes = R.string.notification_channel_reminder_name,
        channelDescriptionRes = R.string.notification_channel_reminder_description,
    ),
    REFLECTION(
        channelId = "affirmity_reflection_prompts",
        notificationId = 1002,
        uniqueWorkName = "affirmity_reflection_chain",
        workTag = "affirmity_reflection",
        prefsPrefix = "reflection",
        channelNameRes = R.string.notification_channel_reflection_name,
        channelDescriptionRes = R.string.notification_channel_reflection_description,
    ),
}
