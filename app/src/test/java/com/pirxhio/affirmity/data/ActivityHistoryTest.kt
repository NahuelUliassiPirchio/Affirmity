package com.pirxhio.affirmity.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityHistoryTest {
    @Test
    fun `unsupported overlapping schema invalidates otherwise proven anonymous coverage`() {
        val full = ActivityCoverageInterval("local:generation", "device", 20_000, 20_002, 1, false)
        assertEquals(ActivityCoverage.UNKNOWN, resolveActivityCoverage("local:generation", 20_001,
            listOf(full, full.copy(recordingSchema = 2))))
        assertEquals(ActivityCoverage.UNKNOWN, resolveActivityCoverage("local:generation", 20_001,
            listOf(full.copy(recordingSchema = 2))))
    }

    @Test
    fun `foreign owner uncertainty never changes local source coverage`() {
        val full = ActivityCoverageInterval("local:generation", "device", 20_000, 20_002, 1, false)
        assertEquals(ActivityCoverage.FULL_SOURCE_DAY, resolveActivityCoverage("local:generation", 20_001,
            listOf(full, full.copy(owner = "account:A", uncertain = true))))
    }

    @Test
    fun `unknown empty day is not confirmed inactivity`() {
        val unknown = aggregateActivityDay(20_000, emptyList(), ActivityCoverage.UNKNOWN)
        val tracked = aggregateActivityDay(20_000, emptyList(), ActivityCoverage.FULL_SOURCE_DAY)
        assertEquals(false, unknown.confirmedEmpty)
        assertEquals(true, tracked.confirmedEmpty)
    }

    @Test
    fun `views count only selected day and do not create meditation time`() {
        val view = ActivityEvent("local:generation", "one", 20_000, ActivityKind.VIEW,
            1_000, 1_000, 1_000, "UTC", null, null, 0, "installation")
        val day = aggregateActivityDay(20_000, listOf(view, view.copy(eventId = "two"),
            view.copy(eventId = "yesterday", epochDay = 19_999)), ActivityCoverage.UNKNOWN)
        assertEquals(2, day.viewedCount)
        assertEquals(0L, day.activeSeconds)
        assertEquals(emptyList<ActivityEvent>(), day.sessions)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `conflicting payload with same identity fails rather than losing a fact`() {
        val view = ActivityEvent("local:generation", "one", 20_000, ActivityKind.VIEW,
            1_000, 1_000, 1_000, "UTC", null, null, 0, "installation")
        aggregateActivityDay(20_000, listOf(view, view.copy(epochDay = 19_999)), ActivityCoverage.UNKNOWN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `combining different owners fails instead of exposing another account`() {
        val view = ActivityEvent("account:A", "one", 20_000, ActivityKind.VIEW,
            1_000, 1_000, 1_000, "UTC", null, null, 0, "installation")
        aggregateActivityDay(20_000, listOf(view, view.copy(owner = "account:B")), ActivityCoverage.UNKNOWN)
    }

    @Test
    fun `source coverage never establishes complete signed in history`() {
        val interval = ActivityCoverageInterval("account:A", "device", 20_000, 20_002, 1, false)
        assertEquals(ActivityCoverage.UNKNOWN, resolveActivityCoverage("account:A", 20_001, listOf(interval)))
    }

    @Test
    fun `anonymous sealed interval excludes uncertainty and unobserved gaps`() {
        val interval = ActivityCoverageInterval("local:generation", "device", 20_000, 20_002, 1, false)
        assertEquals(ActivityCoverage.FULL_SOURCE_DAY, resolveActivityCoverage("local:generation", 20_001, listOf(interval)))
        assertEquals(ActivityCoverage.UNKNOWN, resolveActivityCoverage("local:generation", 19_999, listOf(interval)))
        assertEquals(ActivityCoverage.UNKNOWN, resolveActivityCoverage("local:generation", 20_001,
            listOf(interval, interval.copy(uncertain = true))))
    }

    @Test
    fun `repeated sessions retain individual duration and sum active time`() {
        val first = ActivityEvent("local:generation", "one", 20_000, ActivityKind.GUIDED,
            1_000, 0, 1_000, "UTC", "breathing", "Breathing", 120, "installation")
        val second = first.copy(eventId = "two", activeSeconds = 180)
        val day = aggregateActivityDay(20_000, listOf(first, second, first), ActivityCoverage.UNKNOWN)
        assertEquals(listOf("one", "two"), day.sessions.map { it.eventId })
        assertEquals(300L, day.activeSeconds)
        assertEquals(ActivityCoverage.UNKNOWN, day.coverage)
    }
}
