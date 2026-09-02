package com.pirxhio.affirmity.notifications

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmMessageHandlerTest {

    private val handler = FcmMessageHandler { channel ->
        when (channel) {
            NotificationChannelSpec.REMINDER -> "Reminder title" to "Reminder body"
            NotificationChannelSpec.REFLECTION -> "Reflection title" to "Reflection body"
            NotificationChannelSpec.MOOD -> "Mood title" to "Mood body"
            NotificationChannelSpec.STREAK -> "Streak title" to "Streak body"
            NotificationChannelSpec.HEALER -> "Healer title" to "Healer body"
            NotificationChannelSpec.MEDITATION_RETURN -> "Meditation return title" to "Meditation return body"
        }
    }

    @Test
    fun `resolves reminder channel to Post with default copy`() {
        val action = handler.resolve(mapOf("channel" to "reminder"))

        assertEquals(FcmAction.Post(NotificationChannelSpec.REMINDER, "Reminder title", "Reminder body"), action)
    }

    @Test
    fun `explicit title and body override the default copy`() {
        val action = handler.resolve(
            mapOf("channel" to "reflection", "title" to "Custom", "body" to "Custom body"),
        )

        assertEquals(FcmAction.Post(NotificationChannelSpec.REFLECTION, "Custom", "Custom body"), action)
    }

    @Test
    fun `day_rollover channel resolves to RefreshWidget`() {
        val action = handler.resolve(mapOf("channel" to "day_rollover"))

        assertEquals(FcmAction.RefreshWidget, action)
    }

    @Test
    fun `unknown channel resolves to Ignore`() {
        val action = handler.resolve(mapOf("channel" to "unknown_channel"))

        assertEquals(FcmAction.Ignore, action)
    }

    @Test
    fun `missing channel key resolves to Ignore`() {
        val action = handler.resolve(emptyMap())

        assertEquals(FcmAction.Ignore, action)
    }

    @Test
    fun `resolves meditation_return wire token to the MEDITATION_RETURN channel`() {
        val action = handler.resolve(mapOf("channel" to "meditation_return"))

        assertEquals(
            FcmAction.Post(
                NotificationChannelSpec.MEDITATION_RETURN,
                "Meditation return title",
                "Meditation return body",
            ),
            action,
        )
    }

    @Test
    fun `V2 payload with server title and body is trusted verbatim over the static fallback`() {
        val action = handler.resolve(
            mapOf(
                "channel" to "streak",
                "title" to "5 días seguidos",
                "body" to "No pierdas tu racha hoy",
                "family" to "streak",
                "variantKey" to "streak_4_13_a",
                "destination" to "streak_action",
                "ctaKey" to "cta_streak",
                "locale" to "es",
                "streakCount" to "5",
            ),
        )

        assertEquals(
            FcmAction.Post(
                channel = NotificationChannelSpec.STREAK,
                title = "5 días seguidos",
                body = "No pierdas tu racha hoy",
                family = "streak",
                variantKey = "streak_4_13_a",
                destination = "streak_action",
                ctaKey = "cta_streak",
                locale = "es",
                streakCount = "5",
            ),
            action,
        )
    }

    @Test
    fun `V2 payload missing server title and body falls back to the single static string, not per-variant copy`() {
        val action = handler.resolve(mapOf("channel" to "reflection", "family" to "reflection"))

        assertEquals(
            FcmAction.Post(
                channel = NotificationChannelSpec.REFLECTION,
                title = "Reflection title",
                body = "Reflection body",
                family = "reflection",
            ),
            action,
        )
    }

    @Test
    fun `expiringToday is parsed from the string literal true and defaults to false otherwise`() {
        val expiring = handler.resolve(mapOf("channel" to "healer", "expiringToday" to "true"))
        val notExpiring = handler.resolve(mapOf("channel" to "healer"))

        assertEquals(true, (expiring as FcmAction.Post).expiringToday)
        assertEquals(false, (notExpiring as FcmAction.Post).expiringToday)
    }

    @Test
    fun `Post action calls poster notify with channel title and body`() = runBlocking {
        val poster = FakeNotificationPoster()
        val action = FcmAction.Post(NotificationChannelSpec.REMINDER, "Title", "Body")

        action.applyTo(poster) { }

        assertEquals(1, poster.calls.size)
        assertEquals(NotificationChannelSpec.REMINDER, poster.calls.single().channel)
        assertEquals("Title", poster.calls.single().title)
        assertEquals("Body", poster.calls.single().body)
    }

    @Test
    fun `Post action forwards questionId so Compass opens the exact question sent`() = runBlocking {
        val poster = FakeNotificationPoster()
        val action = handler.resolve(
            mapOf("channel" to "reflection", "questionId" to "q_042"),
        )

        action.applyTo(poster) { }

        assertEquals("q_042", poster.calls.single().questionId)
    }

    @Test
    fun `Post action forwards family, variantKey, locale for notification analytics attribution`() = runBlocking {
        val poster = FakeNotificationPoster()
        val action = handler.resolve(
            mapOf(
                "channel" to "streak",
                "family" to "streak",
                "variantKey" to "streak_4_13_a",
                "locale" to "es",
            ),
        )

        action.applyTo(poster) { }

        val call = poster.calls.single()
        assertEquals("streak", call.family)
        assertEquals("streak_4_13_a", call.variantKey)
        assertEquals("es", call.locale)
    }

    @Test
    fun `RefreshWidget action invokes refresh and never posts`() = runBlocking {
        val poster = FakeNotificationPoster()
        var refreshed = false

        FcmAction.RefreshWidget.applyTo(poster) { refreshed = true }

        assertTrue(refreshed)
        assertTrue(poster.calls.isEmpty())
    }

    @Test
    fun `Ignore action never posts and never refreshes`() = runBlocking {
        val poster = FakeNotificationPoster()
        var refreshed = false

        FcmAction.Ignore.applyTo(poster) { refreshed = true }

        assertTrue(poster.calls.isEmpty())
        assertTrue(!refreshed)
    }

    private class FakeNotificationPoster : NotificationPoster {
        data class Call(
            val channel: NotificationChannelSpec,
            val title: String,
            val body: String,
            val questionId: String? = null,
            val family: String? = null,
            val variantKey: String? = null,
            val locale: String? = null,
        )

        val calls = mutableListOf<Call>()

        override suspend fun notify(
            channel: NotificationChannelSpec,
            title: String,
            body: String,
            attribution: NotificationAttribution,
        ) {
            calls.add(
                Call(
                    channel,
                    title,
                    body,
                    attribution.questionId,
                    attribution.family,
                    attribution.variantKey,
                    attribution.locale,
                ),
            )
        }
    }
}
