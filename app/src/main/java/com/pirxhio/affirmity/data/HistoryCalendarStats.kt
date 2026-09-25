package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import java.util.Calendar

/** Per-day render state for the history calendar (design.md's "Day-state precedence" decision:
 *  `FUTURE > BEFORE_HISTORY > HEALED > DONE > NOT_DONE`). */
enum class HistoryDayStatus { FUTURE, BEFORE_HISTORY, HEALED, DONE, NOT_DONE }

data class HistoryDay(
    val dayOfMonth: Int,
    val epochDay: Long,
    val status: HistoryDayStatus,
    val isToday: Boolean,
)

data class HistoryMonth(val leadingBlanks: Int, val days: List<HistoryDay>)

/** Per-range completion + healer-use slice for the history calendar (design.md's "Loading"
 *  decision -- one month at a time, never the whole history). Habit-agnostic on purpose:
 *  [rows] carries both habits' flags; [healedDays] is the general streak's healed set (healer
 *  uses are not per-habit), matching how [com.pirxhio.affirmity.ui.progress.WeeklyStreakTracker]
 *  already renders healed days for both trackers from the same event log. */
data class CompletionHistorySlice(
    val rows: List<DailyCompletionEntity>,
    val healedDays: Set<Long>,
)

/** One real calendar-day cell before day-state derivation. */
data class HistoryLayoutCell(val dayOfMonth: Int, val epochDay: Long)

/**
 * A calendar month's raw shape: leading blank cells before day 1 (rotated by [template]'s
 * `firstDayOfWeek`, matching `R.array.weekday_letters`'s Sun..Sat order) plus each real day's
 * [DayClock]-consistent `epochDay`. Cell `epochDay`s are walked per-date via `Calendar.add`
 * (design.md's "Cell epochDay" decision), not `monthStart + i` arithmetic, so each cell resolves
 * exactly like the completion writer did across a DST boundary.
 */
data class HistoryMonthLayout(val leadingBlanks: Int, val cells: List<HistoryLayoutCell>) {
    val dayCount: Int get() = cells.size

    companion object {
        fun of(year: Int, month0: Int, template: Calendar): HistoryMonthLayout {
            val firstOfMonth = (template.clone() as Calendar).apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month0)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val firstDayOfWeek = firstOfMonth.firstDayOfWeek
            val dayOfWeek = firstOfMonth.get(Calendar.DAY_OF_WEEK)
            val leadingBlanks = (dayOfWeek - firstDayOfWeek + 7) % 7
            val daysInMonth = firstOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

            val cursor = firstOfMonth.clone() as Calendar
            val cells = (1..daysInMonth).map { day ->
                val epochDay = DayClock.epochDay(cursor)
                val cell = HistoryLayoutCell(dayOfMonth = day, epochDay = epochDay)
                cursor.add(Calendar.DAY_OF_MONTH, 1)
                cell
            }
            return HistoryMonthLayout(leadingBlanks, cells)
        }
    }
}

/** Pure day-state derivation for the history calendar -- no I/O, no `Calendar` reads (mirrors
 *  [DailyCompletionStats]/[StreakHealerStats]'s style). */
object HistoryCalendarStats {

    /**
     * Maps [layout]'s raw cells to render-ready [HistoryDay]s using [slice] and the per-habit
     * [isDone] selector. [earliestEpochDay] `null` means no `daily_completion` row exists yet --
     * the current month is then the lower bound and no cell is ever [HistoryDayStatus.BEFORE_HISTORY]
     * (design.md's "Empty history" decision).
     */
    fun dayStates(
        layout: HistoryMonthLayout,
        slice: CompletionHistorySlice,
        todayEpochDay: Long,
        earliestEpochDay: Long?,
        isDone: (DailyCompletionEntity) -> Boolean,
    ): HistoryMonth {
        val byDay = slice.rows.associateBy { it.epochDay }
        val days = layout.cells.map { cell ->
            val status = when {
                cell.epochDay > todayEpochDay -> HistoryDayStatus.FUTURE
                earliestEpochDay != null && cell.epochDay < earliestEpochDay -> HistoryDayStatus.BEFORE_HISTORY
                cell.epochDay in slice.healedDays -> HistoryDayStatus.HEALED
                byDay[cell.epochDay]?.let(isDone) == true -> HistoryDayStatus.DONE
                else -> HistoryDayStatus.NOT_DONE
            }
            HistoryDay(
                dayOfMonth = cell.dayOfMonth,
                epochDay = cell.epochDay,
                status = status,
                isToday = cell.epochDay == todayEpochDay,
            )
        }
        return HistoryMonth(leadingBlanks = layout.leadingBlanks, days = days)
    }
}
