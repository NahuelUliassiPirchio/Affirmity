package com.pirxhio.affirmity.notifications

/**
 * Activity of a legacy activity-specific streak notification (wire values of the FCM `activity`).
 * LEGACY-REMOVAL: exists only for payloads from servers older than commit fa0b50b (which stopped sending `activity`). Delete once the functions version containing fa0b50b has been live long enough that no queued Cloud Tasks from the previous version remain (check the Cloud Tasks streak queue is empty of pre-fa0b50b tasks), then drop `activity`, StreakActivity and the chip.
 */
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

    /**
     * Warm/energetic: flame + prominent overall [count]. [meditationDays]/[affirmationsDays] are
     * each activity's own streak (null when not live, never 0) for the expanded breakdown.
     * [activity] is only set by legacy payloads from older servers.
     */
    data class Streak(
        override val title: String,
        override val body: String,
        val count: Int?,
        val activity: StreakActivity?,
        val meditationDays: Int? = null,
        val affirmationsDays: Int? = null,
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
        count = attribution.streakCount.positiveIntOrNull(),
        activity = StreakActivity.fromWire(attribution.activity),
        meditationDays = attribution.meditationStreak.positiveIntOrNull(),
        affirmationsDays = attribution.affirmationsStreak.positiveIntOrNull(),
    )
    NotificationChannelSpec.REFLECTION -> NotificationStyleSpec.Reflection(title, body)
    NotificationChannelSpec.MOOD -> NotificationStyleSpec.Mood(title, body)
    else -> NotificationStyleSpec.Plain(title, body)
}

private fun String?.positiveIntOrNull(): Int? = this?.toIntOrNull()?.takeIf { it > 0 }

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

/** Pure decision for one expanded-streak breakdown row (JVM-testable; [Notifier] applies it). */
sealed interface BreakdownRow {
    data object Hidden : BreakdownRow
    data class Visible(val text: String) : BreakdownRow
}

/**
 * Hidden when [days] is null or not positive (a 0-day streak is never printed); otherwise the
 * "label: N days" line, with pluralization delegated to [daysText] (an Android plurals lookup).
 */
fun breakdownRow(
    days: Int?,
    label: String,
    daysText: (Int) -> String,
    line: (label: String, days: String) -> String,
): BreakdownRow =
    if (days == null || days <= 0) BreakdownRow.Hidden else BreakdownRow.Visible(line(label, daysText(days)))
