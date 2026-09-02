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
            ),
        )
        FcmAction.RefreshWidget -> refreshWidget()
        FcmAction.Ignore -> Unit
    }
}
