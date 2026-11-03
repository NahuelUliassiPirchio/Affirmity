package com.pirxhio.affirmity.ui.streak

import com.pirxhio.affirmity.analytics.DailyGoal
import com.pirxhio.affirmity.data.DayRiskEvaluator
import com.pirxhio.affirmity.data.DayRiskState
import com.pirxhio.affirmity.widget.widgetBandFor
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayRiskBannerLogicTest {

    private val london = ZoneId.of("Europe/London")

    private fun millis(text: String) = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    private fun band(state: DayRiskState) = widgetBandFor(state, tone = null)

    @Test
    fun `shows for goal incomplete and streak at risk`() {
        assertTrue(shouldShowDayRiskBanner(band(DayRiskState.GoalIncomplete(DailyGoal.MEDITATION)), null, 100))
        assertTrue(shouldShowDayRiskBanner(band(DayRiskState.StreakAtRisk), null, 100))
    }

    @Test
    fun `hidden for done, in progress and not started`() {
        assertFalse(shouldShowDayRiskBanner(band(DayRiskState.Done), null, 100))
        assertFalse(shouldShowDayRiskBanner(band(DayRiskState.InProgress(setOf(DailyGoal.MEDITATION))), null, 100))
        assertFalse(shouldShowDayRiskBanner(band(DayRiskState.NotStarted), null, 100))
    }

    @Test
    fun `dismissal hides only for the dismissed day`() {
        val risky = band(DayRiskState.StreakAtRisk)
        assertFalse(shouldShowDayRiskBanner(risky, dismissedDay = 100, today = 100))
        assertTrue(shouldShowDayRiskBanner(risky, dismissedDay = 100, today = 101))
    }

    @Test
    fun `evaluator plus band shows only from the threshold`() {
        val before = DayRiskEvaluator.evaluate(false, true, 5, minuteOfDay = 19 * 60 + 59)
        val after = DayRiskEvaluator.evaluate(false, true, 5, minuteOfDay = 20 * 60)
        assertFalse(band(before).visible)
        assertTrue(band(after).visible)
    }

    @Test
    fun `before 20h the next wakeup is 20h today`() {
        val now = millis("2026-06-10T09:30:00+01:00[Europe/London]")
        assertEquals(millis("2026-06-10T20:00:00+01:00[Europe/London]"), nextDayRiskWakeupMillis(now, london))
    }

    @Test
    fun `after 20h the next wakeup is the next local midnight`() {
        val now = millis("2026-06-10T21:15:00+01:00[Europe/London]")
        assertEquals(millis("2026-06-11T00:00:00+01:00[Europe/London]"), nextDayRiskWakeupMillis(now, london))
    }

    @Test
    fun `exactly at 20h the next wakeup is midnight`() {
        val now = millis("2026-06-10T20:00:00+01:00[Europe/London]")
        assertEquals(millis("2026-06-11T00:00:00+01:00[Europe/London]"), nextDayRiskWakeupMillis(now, london))
    }

    @Test
    fun `spring-forward day in London wakes at 20h BST and next midnight BST`() {
        // 2026-03-29: clocks go 01:00 GMT -> 02:00 BST (a 23h day).
        val morning = millis("2026-03-29T08:00:00+01:00[Europe/London]")
        assertEquals(millis("2026-03-29T20:00:00+01:00[Europe/London]"), nextDayRiskWakeupMillis(morning, london))
        val evening = millis("2026-03-29T21:00:00+01:00[Europe/London]")
        assertEquals(millis("2026-03-30T00:00:00+01:00[Europe/London]"), nextDayRiskWakeupMillis(evening, london))
    }

    @Test
    fun `fall-back day in London wakes at 20h GMT and next midnight GMT`() {
        // 2026-10-25: clocks go 02:00 BST -> 01:00 GMT (a 25h day).
        val morning = millis("2026-10-25T08:00:00+00:00[Europe/London]")
        assertEquals(millis("2026-10-25T20:00:00+00:00[Europe/London]"), nextDayRiskWakeupMillis(morning, london))
        val evening = millis("2026-10-25T21:00:00+00:00[Europe/London]")
        assertEquals(millis("2026-10-26T00:00:00+00:00[Europe/London]"), nextDayRiskWakeupMillis(evening, london))
    }

    @Test
    fun `clock reading uses the zone's local day and minute`() {
        val reading = dayClockReading(millis("2026-03-29T23:30:00+01:00[Europe/London]"), london)
        assertEquals(LocalDate.of(2026, 3, 29).toEpochDay(), reading.epochDay)
        assertEquals(23 * 60 + 30, reading.minuteOfDay)
    }
}
