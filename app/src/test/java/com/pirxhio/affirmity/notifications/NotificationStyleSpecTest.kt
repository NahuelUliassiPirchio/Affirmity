package com.pirxhio.affirmity.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationStyleSpecTest {

    @Test
    fun `streak with a numeric count maps to a general streak spec`() {
        val spec = notificationStyleSpec(
            NotificationChannelSpec.STREAK,
            "title",
            "body",
            NotificationAttribution(streakCount = "7"),
        )

        assertEquals(NotificationStyleSpec.Streak("title", "body", count = 7, activity = null), spec)
    }

    @Test
    fun `streak with a known activity carries the activity`() {
        val spec = notificationStyleSpec(
            NotificationChannelSpec.STREAK,
            "t",
            "b",
            NotificationAttribution(streakCount = "5", activity = "meditation"),
        ) as NotificationStyleSpec.Streak

        assertEquals(StreakActivity.MEDITATION, spec.activity)
        assertEquals(5, spec.count)
    }

    @Test
    fun `streak with affirmations activity is recognised`() {
        val spec = notificationStyleSpec(
            NotificationChannelSpec.STREAK,
            "t",
            "b",
            NotificationAttribution(streakCount = "3", activity = "affirmations"),
        ) as NotificationStyleSpec.Streak

        assertEquals(StreakActivity.AFFIRMATIONS, spec.activity)
    }

    @Test
    fun `unknown activity and unparseable or non-positive counts degrade gracefully`() {
        val unknown = notificationStyleSpec(
            NotificationChannelSpec.STREAK, "t", "b",
            NotificationAttribution(streakCount = "abc", activity = "yoga"),
        ) as NotificationStyleSpec.Streak
        val zero = notificationStyleSpec(
            NotificationChannelSpec.STREAK, "t", "b", NotificationAttribution(streakCount = "0"),
        ) as NotificationStyleSpec.Streak

        assertNull(unknown.count)
        assertNull(unknown.activity)
        assertNull(zero.count)
    }

    @Test
    fun `reflection maps to a quote-style spec with the question as body`() {
        val spec = notificationStyleSpec(
            NotificationChannelSpec.REFLECTION, "Moment", "What are you avoiding?", NotificationAttribution(),
        )

        assertEquals(NotificationStyleSpec.Reflection("Moment", "What are you avoiding?"), spec)
    }

    @Test
    fun `mood maps to a text-only spec carrying title and body`() {
        val spec = notificationStyleSpec(
            NotificationChannelSpec.MOOD, "How?", "Tap", NotificationAttribution(),
        )

        assertEquals(NotificationStyleSpec.Mood("How?", "Tap"), spec)
    }

    @Test
    fun `other channels keep the plain text fallback`() {
        NotificationChannelSpec.entries
            .filter { it !in setOf(NotificationChannelSpec.STREAK, NotificationChannelSpec.REFLECTION, NotificationChannelSpec.MOOD) }
            .forEach { channel ->
                val spec = notificationStyleSpec(channel, "t", "b", NotificationAttribution())
                assertTrue("$channel", spec is NotificationStyleSpec.Plain)
            }
    }
}
