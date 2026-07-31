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
            NotificationChannelSpec.STREAK -> "Streak title" to "Streak body"
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
        data class Call(val channel: NotificationChannelSpec, val title: String, val body: String)

        val calls = mutableListOf<Call>()

        override suspend fun notify(channel: NotificationChannelSpec, title: String, body: String) {
            calls.add(Call(channel, title, body))
        }
    }
}
