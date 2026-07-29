package com.pirxhio.affirmity.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Covers tasks 3.3/3.4: non-contiguous week render (spec: home-widget, "Non-contiguous week
 * renders correctly") and the "Empezá hoy" first-run empty state (spec: "First-run empty state").
 */
class WeeklyTrackerWidgetContentTest {

    private val monday = 100L

    @Test
    fun nonContiguousWeek_rendersGridNotEmptyState() = runTest {
        runGlanceAppWidgetUnitTest {
            provideComposable {
                WeeklyTrackerContent(
                    hasAny = true,
                    rows = listOf(
                        DailyCompletionEntity(epochDay = monday, meditationDone = true, affirmationDone = true),
                        DailyCompletionEntity(epochDay = monday + 2, meditationDone = true, affirmationDone = true),
                    ),
                    weekStart = monday,
                    todayIndex = 2,
                )
            }

            onNode(hasText("Afirmar")).assertExists()
        }
    }

    @Test
    fun freshInstall_showsEmptyStatePromptNotGrid() = runTest {
        runGlanceAppWidgetUnitTest {
            provideComposable {
                WeeklyTrackerContent(
                    hasAny = false,
                    rows = emptyList(),
                    weekStart = monday,
                    todayIndex = 0,
                )
            }

            onNode(hasText("Empezá hoy")).assertExists()
        }
    }
}
