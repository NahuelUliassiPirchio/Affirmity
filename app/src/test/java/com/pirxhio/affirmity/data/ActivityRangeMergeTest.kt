package com.pirxhio.affirmity.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityRangeMergeTest {
    private val event = ActivityEvent("account:A", "one", 20_000, ActivityKind.GUIDED,
        1_000, 0, 1_000, "UTC", "breathing", "Breathing", 120, "device")

    @Test fun `remote acknowledgement racing pending snapshot contributes one session`() {
        val merged = mergeActivityEvents("account:A", listOf(event), listOf(event, event.copy(eventId = "two")))
        assertEquals(listOf("one", "two"), merged.map { it.eventId })
        assertEquals(240L, aggregateActivityDay(20_000, merged, ActivityCoverage.UNKNOWN).activeSeconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `foreign owner cannot appear through pending union`() {
        mergeActivityEvents("account:B", emptyList(), listOf(event))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `conflicting remote identity fails rather than choosing silently`() {
        mergeActivityEvents("account:A", listOf(event), listOf(event.copy(activeSeconds = 180)))
    }
}
