package com.pirxhio.affirmity.notifications

import com.pirxhio.affirmity.data.local.NotificationLogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreviewTest {

    private val handler = FcmMessageHandler { "default title" to "default body" }

    private fun resolve(case: NotificationPreviewCase): FcmAction.Post =
        handler.resolve(notificationPreviewData(case, "T", "B", "en")) as FcmAction.Post

    @Test
    fun `streak general has 5 days and no activity`() {
        val post = resolve(NotificationPreviewCase.STREAK_GENERAL)

        assertEquals(NotificationChannelSpec.STREAK, post.channel)
        assertEquals("5", post.streakCount)
        assertNull(post.activity)
        assertEquals("streak", post.family)
        assertEquals("streak_action", post.destination)
    }

    @Test
    fun `streak meditation carries the meditation activity`() {
        val post = resolve(NotificationPreviewCase.STREAK_MEDITATION)

        assertEquals(NotificationChannelSpec.STREAK, post.channel)
        assertEquals("5", post.streakCount)
        assertEquals("meditation", post.activity)
    }

    @Test
    fun `streak affirmations carries the affirmations activity`() {
        val post = resolve(NotificationPreviewCase.STREAK_AFFIRMATIONS)

        assertEquals("affirmations", post.activity)
        assertEquals("5", post.streakCount)
    }

    @Test
    fun `reflection uses the reflection channel with a question id and the body as the question`() {
        val post = resolve(NotificationPreviewCase.REFLECTION)

        assertEquals(NotificationChannelSpec.REFLECTION, post.channel)
        assertEquals("B", post.body)
        assertEquals("compass_question", post.destination)
        assertTrue(!post.questionId.isNullOrBlank())
        assertNull(post.streakCount)
    }

    @Test
    fun `mood uses the mood channel and destination so the picker opens as in production`() {
        val post = resolve(NotificationPreviewCase.MOOD)

        assertEquals(NotificationChannelSpec.MOOD, post.channel)
        assertEquals("mood_checkin", post.destination)
        assertTrue(notificationOpensMoodPicker(post.channel))
    }

    @Test
    fun `plain style uses a channel that has no custom look`() {
        val post = resolve(NotificationPreviewCase.PLAIN)

        val spec = notificationStyleSpec(post.channel, post.title, post.body, NotificationAttribution())
        assertTrue(spec is NotificationStyleSpec.Plain)
    }

    @Test
    fun `every case forwards title body and locale and marks itself as a preview variant`() {
        NotificationPreviewCase.entries.forEach { case ->
            val post = resolve(case)
            assertEquals(case.name, "T", post.title)
            assertEquals(case.name, "B", post.body)
            assertEquals(case.name, "en", post.locale)
            assertTrue(case.name, post.variantKey!!.startsWith("debug_preview_"))
        }
    }

    @Test
    fun `preview blocker maps skip events to user-facing reasons`() {
        assertEquals(
            PreviewBlock.PERMISSION,
            previewBlock(NotificationLogEvent.NOTIFY_SKIPPED_PERMISSION),
        )
        assertEquals(
            PreviewBlock.CHANNEL_BLOCKED,
            previewBlock(NotificationLogEvent.NOTIFY_SKIPPED_CHANNEL_BLOCKED),
        )
        assertNull(previewBlock(null))
        assertFalse(previewBlock(NotificationLogEvent.NOTIFY_POSTED) != null)
    }
}
