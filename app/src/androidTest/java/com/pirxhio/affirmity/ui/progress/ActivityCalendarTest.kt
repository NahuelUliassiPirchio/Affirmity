package com.pirxhio.affirmity.ui.progress

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import com.pirxhio.affirmity.data.*
import com.pirxhio.affirmity.R
import java.util.Calendar
import java.text.DateFormat
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class ActivityCalendarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun selectedDayRetainsRepeatedNamedSessionsAndLatestMood() {
        val day = DayClock.epochDay()
        val event = ActivityEvent("local:G", "one", day, ActivityKind.GUIDED, 100, 0, 100,
            "UTC", "breathing", "Breathing snapshot", 12, "device")
        val activity = aggregateActivityDay(day, listOf(event, event.copy(eventId = "two", nameSnapshot = "Second session", activeSeconds = 18),
            event.copy(eventId = "view1", kind = ActivityKind.VIEW, meditationId = null, nameSnapshot = null, activeSeconds = 0),
            event.copy(eventId = "view2", kind = ActivityKind.VIEW, meditationId = null, nameSnapshot = null, activeSeconds = 0)), ActivityCoverage.UNKNOWN)
        compose.setContent {
            MaterialTheme {
                ActivityCalendar(observeMonth = { _, _ -> flowOf(ActivityMonthState.Ready(mapOf(day to
                    ActivityCalendarDay(activity, com.pirxhio.affirmity.data.local.DailyMoodEntity(day, 5, null), null, false)), 0)) })
            }
        }
        compose.onNodeWithText("Breathing snapshot", substring = true).assertExists()
        compose.onNodeWithText("Second session", substring = true).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.activity_mood_value, 5)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.activity_viewed_count, 2)).assertExists()
        val duration = compose.activity.getString(R.string.activity_duration_format, 0L, 30L)
        compose.onNodeWithText(compose.activity.getString(R.string.activity_total_duration, duration)).assertExists()
        compose.onNodeWithText(fullDate(day)).assertExists()
    }

    @Test fun previousMonthMoodOnlyDayCanBeSelectedWithoutInventedZeroMetrics() {
        val targetCalendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 2) }
        val target = DayClock.epochDay(targetCalendar)
        compose.setContent {
            MaterialTheme {
                ActivityCalendar(observeMonth = { from, to -> flowOf(ActivityMonthState.Ready((from..to).associateWith { day ->
                    ActivityCalendarDay(aggregateActivityDay(day, emptyList(), ActivityCoverage.UNKNOWN),
                        if (day == target) com.pirxhio.affirmity.data.local.DailyMoodEntity(day, 3, "Historical mood") else null, null, false)
                }, 0)) })
            }
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.activity_previous_month)).performClick()
        compose.onNodeWithText("Historical mood").assertDoesNotExist()
        compose.onNodeWithContentDescription(fullDate(target), substring = true).performClick().assertIsSelected()
        compose.onNodeWithText(fullDate(target)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.activity_mood_value, 3)).assertExists()
        compose.onNodeWithText("Historical mood").assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.activity_unknown)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.activity_viewed_count, 0)).assertDoesNotExist()
    }

    @Test fun retryInvokesRecoveryAndReplacesFailureWithActualReadyDetails() {
        var recovered = false
        var retries = 0
        val today = DayClock.epochDay()
        compose.setContent {
            MaterialTheme {
                ActivityCalendar(observeMonth = { _, _ ->
                    flowOf(if (!recovered) ActivityMonthState.Error(IllegalStateException("Offline"))
                        else ActivityMonthState.Ready(mapOf(today to ActivityCalendarDay(
                            aggregateActivityDay(today, emptyList(), ActivityCoverage.FULL_SOURCE_DAY), null, null, false)), 0))
                }, onRetry = { retries++; recovered = true })
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.activity_load_error)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.activity_retry)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.activity_empty)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.activity_viewed_count, 0)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.activity_load_error)).assertDoesNotExist()
        org.junit.Assert.assertEquals(1, retries)
    }

    private fun setCalendarAt(width: Int, fontScale: Float = 1f, bleed: Dp = 0.dp) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(width.dp).padding(ProgressPagePadding)) {
                        ActivityCalendar(observeMonth = { _, _ -> flowOf(ActivityMonthState.Ready(emptyMap(), 0)) }, gridBleed = bleed)
                    }
                }
            }
        }
    }

    @Test fun allSevenColumnsAreVisibleWithoutScrollingOnATypicalPhone() {
        setCalendarAt(411)
        compose.onNodeWithText(compose.activity.getString(R.string.activity_scroll_hint)).assertDoesNotExist()
        val last = compose.onNodeWithTag("calendar-weekday-6").getUnclippedBoundsInRoot()
        val first = compose.onNodeWithTag("calendar-weekday-0").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("Saturday column must be inside the 411dp-24dp page: ${last.right}", last.right <= (411.dp - ProgressPagePadding) + 0.5.dp)
        org.junit.Assert.assertTrue(first.left >= ProgressPagePadding)
        org.junit.Assert.assertTrue("columns must be at least 48dp wide", last.right - last.left >= 48.dp)
    }

    @Test fun weekdayHeadersStaySingleLineAtLargeFontScale() {
        setCalendarAt(411, fontScale = 2f)
        repeat(7) {
            val b = compose.onNodeWithTag("calendar-weekday-$it").getUnclippedBoundsInRoot()
            org.junit.Assert.assertTrue("header $it wrapped: height ${b.bottom - b.top}", b.bottom - b.top <= 40.dp)
        }
    }

    @Test fun threeSixtyDpPhoneFitsAllColumnsByBleedingIntoPagePadding() {
        setCalendarAt(360, bleed = ProgressCalendarBleed)
        compose.onNodeWithText(compose.activity.getString(R.string.activity_scroll_hint)).assertDoesNotExist()
        val last = compose.onNodeWithTag("calendar-weekday-6").getUnclippedBoundsInRoot()
        val first = compose.onNodeWithTag("calendar-weekday-0").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("grid must stay inside the 360dp screen: ${last.right}", last.right <= 360.dp + 0.5.dp)
        org.junit.Assert.assertTrue(first.left >= 0.dp)
    }

    @Test fun narrowWidthKeepsTheHorizontalScrollFallback() {
        setCalendarAt(320)
        compose.onNodeWithText(compose.activity.getString(R.string.activity_scroll_hint)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.activity_scroll_end)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.activity_scroll_start)).assertExists()
    }

    private fun fullDate(day: Long): String = DateFormat.getDateInstance(DateFormat.FULL,
        compose.activity.resources.configuration.locales[0]).format(DayClock.calendarForEpochDay(day).time)
}
