package com.pirxhio.affirmity.notifications

/** Something that can post a notification for a given channel. Implemented by [Notifier]. */
interface NotificationPoster {
    suspend fun notify(channel: NotificationChannelSpec, title: String, body: String)
}

/**
 * Resolved action for an incoming FCM data message (design.md's "Client testability" decision:
 * keep the JVM-testable part pure, no `FirebaseMessagingService`/Robolectric dependency).
 */
sealed interface FcmAction {
    data class Post(val channel: NotificationChannelSpec, val title: String, val body: String) : FcmAction
    data object RefreshWidget : FcmAction
    data object Ignore : FcmAction
}

private const val DAY_ROLLOVER_CHANNEL_KEY = "day_rollover"

/**
 * Pure map -> [FcmAction] resolver (JVM-testable, no Android deps). The server sends a data-only
 * FCM payload (design.md's Message shape decision), so [strings] supplies each channel's default
 * title/body; the payload MAY still override either via explicit `title`/`body` keys.
 */
class FcmMessageHandler(private val strings: (NotificationChannelSpec) -> Pair<String, String>) {

    fun resolve(data: Map<String, String>): FcmAction {
        val channelKey = data["channel"] ?: return FcmAction.Ignore
        if (channelKey == DAY_ROLLOVER_CHANNEL_KEY) return FcmAction.RefreshWidget

        val channel = NotificationChannelSpec.entries.firstOrNull { it.prefsPrefix == channelKey }
            ?: return FcmAction.Ignore
        val (defaultTitle, defaultBody) = strings(channel)
        return FcmAction.Post(
            channel = channel,
            title = data["title"] ?: defaultTitle,
            body = data["body"] ?: defaultBody,
        )
    }
}

/** Applies a resolved [FcmAction]: posts via [poster], refreshes the widget, or does nothing. */
suspend fun FcmAction.applyTo(poster: NotificationPoster, refreshWidget: suspend () -> Unit) {
    when (this) {
        is FcmAction.Post -> poster.notify(channel, title, body)
        FcmAction.RefreshWidget -> refreshWidget()
        FcmAction.Ignore -> Unit
    }
}
