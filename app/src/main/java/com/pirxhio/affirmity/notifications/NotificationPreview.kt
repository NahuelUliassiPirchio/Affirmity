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
    val meditationStreak: Int? = null,
    val affirmationsStreak: Int? = null,
    @StringRes val labelRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
) {
    STREAK_BOTH(
        channel = NotificationChannelSpec.STREAK,
        streakCount = PREVIEW_BOTH_MEDITATION_DAYS,
        meditationStreak = PREVIEW_BOTH_MEDITATION_DAYS,
        affirmationsStreak = PREVIEW_BOTH_AFFIRMATIONS_DAYS,
        labelRes = R.string.notification_debug_preview_streak_both,
        titleRes = R.string.notification_debug_preview_streak_both_title,
        bodyRes = R.string.notification_debug_preview_streak_both_body,
    ),
    STREAK_MEDITATION_ONLY(
        channel = NotificationChannelSpec.STREAK,
        streakCount = PREVIEW_STREAK_DAYS,
        meditationStreak = PREVIEW_STREAK_DAYS,
        affirmationsStreak = 0,
        labelRes = R.string.notification_debug_preview_streak_meditation_only,
        titleRes = R.string.notification_debug_preview_streak_meditation_only_title,
        bodyRes = R.string.notification_debug_preview_streak_meditation_only_body,
    ),
    STREAK_AFFIRMATIONS_ONLY(
        channel = NotificationChannelSpec.STREAK,
        streakCount = PREVIEW_STREAK_DAYS,
        meditationStreak = 0,
        affirmationsStreak = PREVIEW_STREAK_DAYS,
        labelRes = R.string.notification_debug_preview_streak_affirmations_only,
        titleRes = R.string.notification_debug_preview_streak_affirmations_only_title,
        bodyRes = R.string.notification_debug_preview_streak_affirmations_only_body,
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
    ;

    /** Format args for [labelRes] and [titleRes]: the overall streak (`%1$d`). */
    val headlineArgs: Array<Any> get() = arrayOf(streakCount ?: 0)

    /** Format args for [bodyRes]: meditation (`%1$d`) and affirmations (`%2$d`) streaks. */
    val bodyArgs: Array<Any> get() = arrayOf(meditationStreak ?: 0, affirmationsStreak ?: 0)
}

private const val PREVIEW_STREAK_DAYS = 5

// STREAK_BOTH shows two different per-activity streaks so the breakdown rows are distinguishable.
// The overall streak is the longer one. The preview strings render these through format
// placeholders, so they cannot drift from the copy.
private const val PREVIEW_BOTH_MEDITATION_DAYS = 7
private const val PREVIEW_BOTH_AFFIRMATIONS_DAYS = 4

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
    case.meditationStreak?.let { put("meditationStreak", it.toString()) }
    case.affirmationsStreak?.let { put("affirmationsStreak", it.toString()) }
    if (case.channel == NotificationChannelSpec.REFLECTION) put("questionId", "debug_preview_question")
}

/** Why a preview could not be shown; drives visible feedback instead of a silent no-op. */
enum class PreviewBlock { PERMISSION, CHANNEL_BLOCKED }

internal fun previewBlock(skipEvent: NotificationLogEvent?): PreviewBlock? = when (skipEvent) {
    NotificationLogEvent.NOTIFY_SKIPPED_PERMISSION -> PreviewBlock.PERMISSION
    NotificationLogEvent.NOTIFY_SKIPPED_CHANNEL_BLOCKED -> PreviewBlock.CHANNEL_BLOCKED
    else -> null
}
