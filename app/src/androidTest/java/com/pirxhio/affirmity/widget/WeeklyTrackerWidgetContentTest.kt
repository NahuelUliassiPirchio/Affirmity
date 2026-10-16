package com.pirxhio.affirmity.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.platform.app.InstrumentationRegistry
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Covers tasks 3.3/3.4: non-contiguous week render (spec: home-widget, "Non-contiguous week
 * renders correctly") and the "Empezá hoy" first-run empty state (spec: "First-run empty state").
 */
class WeeklyTrackerWidgetContentTest {

    private val monday = 100L
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun nonContiguousWeek_rendersGridNotEmptyState() = runTest {
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                WeeklyTrackerContent(
                    hasAny = true,
                    rows = listOf(
                        DailyCompletionEntity(epochDay = monday, meditationDone = true, affirmationDone = true),
                        DailyCompletionEntity(epochDay = monday + 2, meditationDone = true, affirmationDone = true),
                    ),
                    weekStart = monday,
                    todayIndex = 2,
                    dayLetters = listOf("L", "M", "M", "J", "V", "S", "D"),
                )
            }

            onNode(hasText(context.getString(R.string.widget_action_affirm_label))).assertExists()
        }
    }

    @Test
    fun freshInstall_showsEmptyStatePromptNotGrid() = runTest {
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                WeeklyTrackerContent(
                    hasAny = false,
                    rows = emptyList(),
                    weekStart = monday,
                    todayIndex = 0,
                    dayLetters = listOf("L", "M", "M", "J", "V", "S", "D"),
                )
            }

            onNode(hasText(context.getString(R.string.widget_empty_state_label))).assertExists()
        }
    }

    private val dayLetters = listOf("L", "M", "M", "J", "V", "S", "D")
    private val regularSize = DpSize(320.dp, 120.dp)

    @Test
    fun streakOfThree_showsTheStreakNumber() = runTest {
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(regularSize)
            provideComposable {
                WeeklyTrackerContent(
                    hasAny = true,
                    rows = emptyList(),
                    weekStart = monday,
                    todayIndex = 6,
                    dayLetters = dayLetters,
                    generalStreakDays = 3,
                    isTodayDone = true,
                )
            }

            onNode(hasText("3")).assertExists()
        }
    }

    @Test
    fun streakOfZero_hidesTheStreakCounter() = runTest {
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(regularSize)
            provideComposable {
                WeeklyTrackerContent(
                    hasAny = true,
                    rows = emptyList(),
                    weekStart = monday,
                    todayIndex = 6,
                    dayLetters = dayLetters,
                    generalStreakDays = 0,
                )
            }

            onNode(hasText("0")).assertDoesNotExist()
            onNode(hasText(context.getString(R.string.widget_action_affirm_label))).assertExists()
        }
    }

    @Test
    fun healedDay_exposesTheHealedDescription() = runTest {
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(regularSize)
            provideComposable {
                WeeklyTrackerContent(
                    hasAny = true,
                    rows = emptyList(),
                    weekStart = monday,
                    todayIndex = 6,
                    dayLetters = dayLetters,
                    generalStreakDays = 5,
                    healedDays = setOf(monday + 2),
                )
            }

            onNode(hasContentDescription(context.getString(R.string.widget_day_healed))).assertExists()
        }
    }
}
