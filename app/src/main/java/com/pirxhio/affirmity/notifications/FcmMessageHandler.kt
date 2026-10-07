package com.pirxhio.affirmity.notifications

/**
 * Groups [NotificationPoster.notify]'s trailing attribution/routing params, which used to be 6
 * separate positionally-adjacent nullable-`String`/`Boolean` parameters (Readability fix).
 *
 * [destination]/[expiringToday]/[questionId] drive delivery mechanics (start destination,
 * conditional HIGH-priority escalation for Healer, the Compass per-instance deep link).
 * [family]/[variantKey]/[locale] (Notifications V2 analytics, design §9) are carried through to
 * the notification's `PendingIntent` extras so `notification_opened`/`notification_action_clicked`
 * can attribute the exact family/variant/locale a tap resolves back to, without a new persistence
 * store.
 */
data class NotificationAttribution(
    val destination: String? = null,
    val expiringToday: Boolean = false,
    val questionId: String? = null,
    val family: String? = null,
    val variantKey: String? = null,
    val locale: String? = null,
    /** Streak count the server rendered into the copy (streak channel only). */
    val streakCount: String? = null,
    /**
     * Legacy: set only by older servers for an activity-specific streak alert. No longer sent.
     * LEGACY-REMOVAL: exists only for payloads from servers older than commit fa0b50b (which stopped sending `activity`). Delete once the functions version containing fa0b50b has been live long enough that no queued Cloud Tasks from the previous version remain (check the Cloud Tasks streak queue is empty of pre-fa0b50b tasks), then drop `activity`, StreakActivity and the chip.
     */
    val activity: String? = null,
    /** Meditation's own streak in days (streak channel only; `0` when it is not live). */
    val meditationStreak: String? = null,
    /** Affirmations' own streak in days (streak channel only; `0` when it is not live). */
    val affirmationsStreak: String? = null,
)

/** Something that can post a notification for a given channel. Implemented by [Notifier]. */
interface NotificationPoster {
    suspend fun notify(
        channel: NotificationChannelSpec,
        title: String,
        body: String,
        attribution: NotificationAttribution = NotificationAttribution(),
    )
}

/**
 * Resolved action for an incoming FCM data message (design.md's "Client testability" decision:
 * keep the JVM-testable part pure, no `FirebaseMessagingService`/Robolectric dependency).
 *
 * The extra fields beyond [channel]/[title]/[body] mirror the server's `V2FcmData` payload
 * (Notifications V2 design §7) and are all optional so a legacy (pre-V2) data-only payload still
 * resolves to a valid [Post].
 */
sealed interface FcmAction {
    data class Post(
        val channel: NotificationChannelSpec,
        val title: String,
        val body: String,
        val family: String? = null,
        val variantKey: String? = null,
        val destination: String? = null,
        val ctaKey: String? = null,
        val locale: String? = null,
        val streakCount: String? = null,
        val inactiveDays: String? = null,
        val questionId: String? = null,
        val expiringToday: Boolean = false,
        /** Optional (backwards compatible): activity an old-server activity-specific alert is about. */
        val activity: String? = null,
        val meditationStreak: String? = null,
        val affirmationsStreak: String? = null,
    ) : FcmAction
    data object RefreshWidget : FcmAction
    data object Ignore : FcmAction
}

private const val DAY_ROLLOVER_CHANNEL_KEY = "day_rollover"

/**
 * Pure map -> [FcmAction] resolver (JVM-testable, no Android deps). The server always renders and
 * sends `title`/`body` at send time (Notifications V2 design §1/§7); [strings] supplies the ONE
 * honest static per-channel fallback used only when the server payload omits them (e.g. catalog
 * resolution failed upstream) — never a per-variant pool.
 */
class FcmMessageHandler(private val strings: (NotificationChannelSpec) -> Pair<String, String>) {

    fun resolve(data: Map<String, String>): FcmAction {
        val channelKey = data["channel"] ?: return FcmAction.Ignore
        if (channelKey == DAY_ROLLOVER_CHANNEL_KEY) return FcmAction.RefreshWidget

        val channel = NotificationChannelSpec.entries.firstOrNull { it.wireChannelKey == channelKey }
            ?: return FcmAction.Ignore
        val (defaultTitle, defaultBody) = strings(channel)
        return FcmAction.Post(
            channel = channel,
            title = data["title"] ?: defaultTitle,
            body = data["body"] ?: defaultBody,
            family = data["family"],
            variantKey = data["variantKey"],
            destination = data["destination"],
            ctaKey = data["ctaKey"],
            locale = data["locale"],
            streakCount = data["streakCount"],
            inactiveDays = data["inactiveDays"],
            questionId = data["questionId"],
            expiringToday = data["expiringToday"] == "true",
            activity = data["activity"],
            meditationStreak = data["meditationStreak"],
            affirmationsStreak = data["affirmationsStreak"],
        )
    }
}

/** Applies a resolved [FcmAction]: posts via [poster], refreshes the widget, or does nothing. */
suspend fun FcmAction.applyTo(poster: NotificationPoster, refreshWidget: suspend () -> Unit) {
    when (this) {
        is FcmAction.Post -> poster.notify(
            channel = channel,
            title = title,
            body = body,
            attribution = NotificationAttribution(
                destination = destination,
                expiringToday = expiringToday,
                questionId = questionId,
                family = family,
                variantKey = variantKey,
                locale = locale,
                streakCount = streakCount,
                activity = activity,
                meditationStreak = meditationStreak,
                affirmationsStreak = affirmationsStreak,
            ),
        )
        FcmAction.RefreshWidget -> refreshWidget()
        FcmAction.Ignore -> Unit
    }
}
