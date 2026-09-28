package com.pirxhio.affirmity.notifications

/** Activity an activity-specific streak notification is about (wire values of the FCM `activity`). */
enum class StreakActivity(val wireValue: String) {
    MEDITATION("meditation"),
    AFFIRMATIONS("affirmations"),
    ;

    companion object {
        fun fromWire(value: String?): StreakActivity? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Framework-free description of how a notification should look. [notificationStyleSpec] decides the
 * per-type look from the channel and payload (JVM-testable); [Notifier] only turns a spec into
 * RemoteViews/builder calls.
 */
sealed interface NotificationStyleSpec {
    val title: String
    val body: String

    /** Warm/energetic: flame + prominent [count]. [activity] is set for activity-specific alerts. */
    data class Streak(
        override val title: String,
        override val body: String,
        val count: Int?,
        val activity: StreakActivity?,
    ) : NotificationStyleSpec

    /** Calm, quote-like: [body] is the question and is rendered large. */
    data class Reflection(override val title: String, override val body: String) : NotificationStyleSpec

    /** Friendly, text-only check-in prompt; tapping opens the mood picker via the content intent. */
    data class Mood(override val title: String, override val body: String) : NotificationStyleSpec

    /** System BigText look, used by every channel without a bespoke design. */
    data class Plain(override val title: String, override val body: String) : NotificationStyleSpec
}

internal fun notificationStyleSpec(
    channel: NotificationChannelSpec,
    title: String,
    body: String,
    attribution: NotificationAttribution,
): NotificationStyleSpec = when (channel) {
    NotificationChannelSpec.STREAK -> NotificationStyleSpec.Streak(
        title = title,
        body = body,
        count = attribution.streakCount?.toIntOrNull()?.takeIf { it > 0 },
        activity = StreakActivity.fromWire(attribution.activity),
    )
    NotificationChannelSpec.REFLECTION -> NotificationStyleSpec.Reflection(title, body)
    NotificationChannelSpec.MOOD -> NotificationStyleSpec.Mood(title, body)
    else -> NotificationStyleSpec.Plain(title, body)
}

/** True for the specs rendered with custom RemoteViews (the ones that may need a plain fallback). */
internal fun NotificationStyleSpec.usesCustomViews(): Boolean = this !is NotificationStyleSpec.Plain

/**
 * Posts with the styled (custom RemoteViews) path and, if it throws, logs via [onFallback] and
 * posts the plain notification instead. Plain-only specs are never retried. A failure of the plain
 * path propagates, so callers only record success after this returns normally.
 */
internal inline fun postWithPlainFallback(
    usesCustomViews: Boolean,
    postStyled: () -> Unit,
    postPlain: () -> Unit,
    onFallback: (Exception) -> Unit,
) {
    if (!usesCustomViews) {
        postStyled()
        return
    }
    try {
        postStyled()
    } catch (error: Exception) {
        onFallback(error)
        postPlain()
    }
}
