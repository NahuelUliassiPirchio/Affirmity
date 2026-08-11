package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.DaySegment
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** See design.md's "Interfaces / Contracts" note: mappers convert 1:1, no cached streak field. */
class FirestoreMappersTest {

    @Test
    fun `affirmation entity round-trips losslessly through a map, including backgroundValue verbatim`() {
        val entity = AffirmationEntity(
            id = "aff-1",
            title = "You are enough",
            subtitle = "Breathe in, breathe out",
            backgroundType = "image",
            backgroundValue = "content://media/external/images/media/42",
        )

        val map = affirmationToMap(entity)
        val roundTripped = affirmationFromMap(map)

        assertEquals(entity, roundTripped)
        assertEquals("content://media/external/images/media/42", map["backgroundValue"])
    }

    @Test
    fun `daily completion entity round-trips and the map carries a numeric epochDay field`() {
        val entity = DailyCompletionEntity(epochDay = 19_876L, meditationDone = true, affirmationDone = false)

        val map = dailyCompletionToMap(entity)
        val roundTripped = dailyCompletionFromMap(map)

        assertEquals(entity, roundTripped)
        assertEquals(19_876L, (map["epochDay"] as Number).toLong())
    }

    @Test
    fun `channel settings round-trip for the reminder channel and no streak field is ever produced`() {
        val settings = ChannelSettings(enabled = true, segments = setOf(DaySegment.MANANA))

        val map = channelSettingsToMap(NotificationChannelSpec.REMINDER, settings)
        val roundTripped = channelSettingsFromMap(NotificationChannelSpec.REMINDER, map)

        assertEquals(settings, roundTripped)
        assertFalse(map.keys.any { it.contains("streak", ignoreCase = true) })
    }

    @Test
    fun `channel settings round-trip for the reflection channel independently of the reminder channel`() {
        val reminder = ChannelSettings(enabled = false, segments = setOf(DaySegment.MANANA, DaySegment.TARDE))
        val reflection = ChannelSettings(enabled = true, segments = setOf(DaySegment.NOCHE))

        val combined = channelSettingsToMap(NotificationChannelSpec.REMINDER, reminder) +
            channelSettingsToMap(NotificationChannelSpec.REFLECTION, reflection)

        assertEquals(reminder, channelSettingsFromMap(NotificationChannelSpec.REMINDER, combined))
        assertEquals(reflection, channelSettingsFromMap(NotificationChannelSpec.REFLECTION, combined))
    }

    @Test
    fun `streak healer use entity round-trips and the map carries a numeric healedEpochDay field`() {
        val entity = StreakHealerUseEntity(healedEpochDay = 19_876L, activatedAtMillis = 1_700_000_000_000L)

        val map = streakHealerUseToMap(entity)
        val roundTripped = streakHealerUseFromMap(map)

        assertEquals(entity, roundTripped)
        assertEquals(19_876L, (map["healedEpochDay"] as Number).toLong())
    }
}
