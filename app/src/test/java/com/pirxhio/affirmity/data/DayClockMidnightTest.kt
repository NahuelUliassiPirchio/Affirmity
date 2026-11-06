package com.pirxhio.affirmity.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DayClockMidnightTest {

    private val london = ZoneId.of("Europe/London")
    private val hourMs = 3_600_000L

    private fun millis(text: String) = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun `delay until next midnight on a regular day`() {
        val now = millis("2026-06-10T22:30:00+01:00[Europe/London]")

        assertEquals(90 * 60_000L, DayClock.millisUntilNextMidnight(now, london))
    }

    @Test
    fun `delay on the 23h spring-forward day is shorter than 24h`() {
        val midnight = millis("2026-03-29T00:00:00+00:00[Europe/London]")

        assertEquals(23 * hourMs, DayClock.millisUntilNextMidnight(midnight, london))
    }

    @Test
    fun `delay on the 25h fall-back day is longer than 24h`() {
        val midnight = millis("2026-10-25T00:00:00+01:00[Europe/London]")

        assertEquals(25 * hourMs, DayClock.millisUntilNextMidnight(midnight, london))
    }

    @Test
    fun `delay is always positive even right at midnight`() {
        val midnight = millis("2026-06-11T00:00:00+01:00[Europe/London]")

        assertEquals(24 * hourMs, DayClock.millisUntilNextMidnight(midnight, london))
    }

    @Test
    fun `epochDayFlow emits now and again after crossing midnight`() = runTest {
        val start = millis("2026-06-10T23:00:00+01:00[Europe/London]")
        val emitted = mutableListOf<Long>()
        backgroundScope.launch {
            DayClock.epochDayFlow(nowMillis = { start + testScheduler.currentTime }, zone = { london })
                .collect { emitted += it }
        }

        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 6, 10).toEpochDay()), emitted)

        advanceTimeBy(hourMs + 1)
        runCurrent()
        assertEquals(
            listOf(LocalDate.of(2026, 6, 10).toEpochDay(), LocalDate.of(2026, 6, 11).toEpochDay()),
            emitted,
        )
    }

    @Test
    fun `recheck re-reads the clock without duplicating an unchanged day`() = runTest {
        var now = millis("2026-06-10T10:00:00+01:00[Europe/London]")
        val recheck = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val emitted = mutableListOf<Long>()
        backgroundScope.launch {
            DayClock.epochDayFlow(recheck, nowMillis = { now }, zone = { london }).collect { emitted += it }
        }
        runCurrent()

        recheck.tryEmit(Unit)
        runCurrent()
        assertEquals(1, emitted.size)

        now = millis("2026-06-12T09:00:00+01:00[Europe/London]")
        recheck.tryEmit(Unit)
        runCurrent()
        assertEquals(LocalDate.of(2026, 6, 12).toEpochDay(), emitted.last())
        assertEquals(2, emitted.size)
    }
}
