package com.pirxhio.affirmity.notifications

import androidx.annotation.StringRes
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.local.NotificationLogEvent

/**
 * Cases the debug screen can preview. Each one is turned into the FCM data map a real server
 * message would carry ([notificationPreviewData]) and fed through [FcmMessageHandler] and
 * [applyTo], the exact path `AffirmityMessagingService.onMessageReceived` uses, so the preview
 * exercises the real Notifier, channels, styling, content intent and start destination.
 */
enum class NotificationPreviewCase(
    val channel: NotificationChannelSpec,
    val streakCount: Int? = null,
    val activity: StreakActivity? = null,
    @StringRes val labelRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
) {
    STREAK_GENERAL(
        channel = NotificationChannelSpec.STREAK,
        streakCount = PREVIEW_STREAK_DAYS,
        labelRes = R.string.notification_debug_preview_streak_general,
        titleRes = R.string.notification_debug_preview_streak_general_title,
        bodyRes = R.string.notification_debug_preview_streak_general_body,
    ),
    STREAK_MEDITATION(
        channel = NotificationChannelSpec.STREAK,
        streakCount = PREVIEW_STREAK_DAYS,
        activity = StreakActivity.MEDITATION,
        labelRes = R.string.notification_debug_preview_streak_meditation,
        titleRes = R.string.notification_debug_preview_streak_meditation_title,
        bodyRes = R.string.notification_debug_preview_streak_meditation_body,
    ),
    STREAK_AFFIRMATIONS(
        channel = NotificationChannelSpec.STREAK,
        streakCount = PREVIEW_STREAK_DAYS,
        activity = StreakActivity.AFFIRMATIONS,
        labelRes = R.string.notification_debug_preview_streak_affirmations,
        titleRes = R.string.notification_debug_preview_streak_affirmations_title,
        bodyRes = R.string.notification_debug_preview_streak_affirmations_body,
    ),
    REFLECTION(
        channel = NotificationChannelSpec.REFLECTION,
        labelRes = R.string.notification_debug_preview_reflection,
        titleRes = R.string.notification_debug_preview_reflection_title,
        bodyRes = R.string.notification_debug_preview_reflection_body,
    ),
    MOOD(
        channel = NotificationChannelSpec.MOOD,
        labelRes = R.string.notification_debug_preview_mood,
        titleRes = R.string.notification_debug_preview_mood_title,
        bodyRes = R.string.notification_debug_preview_mood_body,
    ),
    PLAIN(
        channel = NotificationChannelSpec.REMINDER,
        labelRes = R.string.notification_debug_preview_plain,
        titleRes = R.string.notification_debug_preview_plain_title,
        bodyRes = R.string.notification_debug_preview_plain_body,
    ),
}

private const val PREVIEW_STREAK_DAYS = 5

/** Wire `destination` token per channel, mirroring `DESTINATION_BY_CHANNEL` in functions/src/index.ts. */
private fun wireDestination(channel: NotificationChannelSpec): String = when (channel) {
    NotificationChannelSpec.REMINDER -> "affirmations_feed"
    NotificationChannelSpec.MOOD -> "mood_checkin"
    NotificationChannelSpec.REFLECTION -> "compass_question"
    NotificationChannelSpec.STREAK -> "streak_action"
    NotificationChannelSpec.HEALER -> "healer_flow"
    NotificationChannelSpec.MEDITATION_RETURN -> "short_meditation"
}

/** Pure builder of the FCM-like data payload for [case]; [title]/[body] are the preview copy. */
fun notificationPreviewData(
    case: NotificationPreviewCase,
    title: String,
    body: String,
    locale: String,
): Map<String, String> = buildMap {
    put("channel", case.channel.wireChannelKey)
    put("title", title)
    put("body", body)
    put("family", case.channel.wireChannelKey)
    put("destination", wireDestination(case.channel))
    put("locale", locale)
    put("variantKey", "debug_preview_${case.name.lowercase()}")
    case.streakCount?.let { put("streakCount", it.toString()) }
    case.activity?.let { put("activity", it.wireValue) }
    if (case.channel == NotificationChannelSpec.REFLECTION) put("questionId", "debug_preview_question")
}

/** Why a preview could not be shown; drives visible feedback instead of a silent no-op. */
enum class PreviewBlock { PERMISSION, CHANNEL_BLOCKED }

internal fun previewBlock(skipEvent: NotificationLogEvent?): PreviewBlock? = when (skipEvent) {
    NotificationLogEvent.NOTIFY_SKIPPED_PERMISSION -> PreviewBlock.PERMISSION
    NotificationLogEvent.NOTIFY_SKIPPED_CHANNEL_BLOCKED -> PreviewBlock.CHANNEL_BLOCKED
    else -> null
}
