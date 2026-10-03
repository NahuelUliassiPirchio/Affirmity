package com.pirxhio.affirmity.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.CompletionHistorySlice
import com.pirxhio.affirmity.data.DayClock
import com.pirxhio.affirmity.data.HistoryCalendarStats
import com.pirxhio.affirmity.data.HistoryDay
import com.pirxhio.affirmity.data.HistoryDayStatus
import com.pirxhio.affirmity.data.HistoryMonthLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.Flow

/** Which habit's history calendar is open (design.md's "Per-Habit Calendar Scope" requirement:
 *  the system never combines both habits into a single calendar view). */
enum class HistoryHabit { AFFIRMATIONS, MEDITATION }

/**
 * Full-screen history calendar for a single [habit] (design.md's "UI shape" decision: a
 * full-screen [Dialog], `usePlatformDefaultWidth = false`, not a bottom sheet or a new
 * destination -- it handles system back through [onDismissRequest] for free).
 *
 * [earliestEpochDayFlow] is collected once per open (design's "Earliest-day API" -- a one-shot
 * per-session value, not re-queried per month). [historyFlowFactory] is invoked with the visible
 * month's day range only (design's "Loading" decision: per visible month, not the whole history
 * at once) and re-subscribed via `remember(monthIndex)` whenever the user navigates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryCalendarDialog(
    habit: HistoryHabit,
    earliestEpochDayFlow: Flow<Long?>,
    historyFlowFactory: (from: Long, to: Long) -> Flow<CompletionHistorySlice>,
    onDismissRequest: () -> Unit,
) {
    val today = remember { DayClock.epochDay() }
    val currentMonthIndex = remember(today) { monthIndexFor(today) }
    var monthIndex by rememberSaveable { mutableIntStateOf(currentMonthIndex) }

    val earliestEpochDay by earliestEpochDayFlow.collectAsState(initial = null)
    val earliestMonthIndex = remember(earliestEpochDay, currentMonthIndex) {
        earliestEpochDay?.let(::monthIndexFor) ?: currentMonthIndex
    }

    val year = monthIndex.floorDiv(12)
    val month0 = monthIndex.mod(12)
    val layout = remember(monthIndex) {
        HistoryMonthLayout.of(year, month0, Calendar.getInstance())
    }
    val historyFlow = remember(monthIndex) {
        historyFlowFactory(layout.cells.first().epochDay, layout.cells.last().epochDay)
    }
    val slice by historyFlow.collectAsState(initial = CompletionHistorySlice(emptyList(), emptySet()))

    val month = remember(layout, slice, earliestEpochDay, habit) {
        HistoryCalendarStats.dayStates(
            layout = layout,
            slice = slice,
            todayEpochDay = today,
            earliestEpochDay = earliestEpochDay,
            isDone = { row -> if (habit == HistoryHabit.AFFIRMATIONS) row.affirmationDone else row.meditationDone },
        )
    }

    var selectedDay by rememberSaveable(monthIndex) { mutableStateOf<Long?>(null) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (habit == HistoryHabit.AFFIRMATIONS) {
                                stringResource(R.string.history_calendar_title_affirmations)
                            } else {
                                stringResource(R.string.history_calendar_title_meditation)
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.history_calendar_close_content_description),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                MonthNavigationHeader(
                    year = year,
                    month0 = month0,
                    canGoBack = monthIndex > earliestMonthIndex,
                    canGoForward = monthIndex < currentMonthIndex,
                    onPrevious = { monthIndex-- },
                    onNext = { monthIndex++ },
                )
                WeekdayHeaderRow()
                MonthGrid(
                    leadingBlanks = month.leadingBlanks,
                    days = month.days,
                    selectedDay = selectedDay,
                    onDayTap = { day ->
                        if (day.status != HistoryDayStatus.FUTURE && day.status != HistoryDayStatus.BEFORE_HISTORY) {
                            selectedDay = day.epochDay
                        }
                    },
                )
                val selected = month.days.firstOrNull { it.epochDay == selectedDay }
                if (selected != null) {
                    Text(
                        text = statusLineFor(selected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

private fun monthIndexFor(epochDay: Long): Int {
    val calendar = DayClock.calendarForEpochDay(epochDay)
    return calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
}

@Composable
private fun MonthNavigationHeader(
    year: Int,
    month0: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val monthLabel = remember(year, month0) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month0)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = canGoBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.history_calendar_prev_month_content_description),
            )
        }
        Text(text = monthLabel, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.history_calendar_next_month_content_description),
            )
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    val letters = stringArrayResource(R.array.weekday_letters) // Sun..Sat
    val firstDayOfWeek = Calendar.getInstance().firstDayOfWeek // 1 (Sun) .. 7 (Sat)
    val rotated = remember(letters, firstDayOfWeek) {
        val offset = firstDayOfWeek - Calendar.SUNDAY
        List(7) { index -> letters[(index + offset) % 7] }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        rotated.forEach { letter ->
            Text(
                text = letter,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    leadingBlanks: Int,
    days: List<HistoryDay>,
    selectedDay: Long?,
    onDayTap: (HistoryDay) -> Unit,
) {
    val cells: List<HistoryDay?> = List(leadingBlanks) { null } + days
    val rows = cells.chunked(7)
    // Every cell, including the leading/trailing blanks, gets the same equal-width bordered square
    // so the days read as one visible grid instead of loose circles. outlineVariant is a theme
    // role, so the lines follow light/dark automatically.
    val gridLine = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, gridLine, RoundedCornerShape(12.dp)),
    ) {
        rows.forEach { rowCells ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowCells.forEach { day ->
                    GridCell(gridLine) {
                        if (day != null) {
                            DayCell(day = day, isSelected = day.epochDay == selectedDay, onTap = { onDayTap(day) })
                        }
                    }
                }
                repeat(7 - rowCells.size) { GridCell(gridLine) {} }
            }
        }
    }
}

@Composable
private fun RowScope.GridCell(lineColor: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .border(0.5.dp, lineColor),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun DayCell(day: HistoryDay, isSelected: Boolean, onTap: () -> Unit) {
    val tappable = day.status != HistoryDayStatus.FUTURE && day.status != HistoryDayStatus.BEFORE_HISTORY
    val backgroundColor = when (day.status) {
        HistoryDayStatus.HEALED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        HistoryDayStatus.DONE -> MaterialTheme.colorScheme.onSurface
        HistoryDayStatus.NOT_DONE -> if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            Color.Transparent
        }
        HistoryDayStatus.FUTURE, HistoryDayStatus.BEFORE_HISTORY -> Color.Transparent
    }
    val textColor = when (day.status) {
        HistoryDayStatus.DONE -> MaterialTheme.colorScheme.surface
        HistoryDayStatus.FUTURE, HistoryDayStatus.BEFORE_HISTORY -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(color = backgroundColor, shape = CircleShape)
            .then(if (tappable) Modifier.clickable(onClick = onTap) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}

@Composable
private fun statusLineFor(day: HistoryDay): String {
    val dateLabel = remember(day.epochDay) {
        val calendar = DayClock.calendarForEpochDay(day.epochDay)
        SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(calendar.time)
    }
    return when (day.status) {
        HistoryDayStatus.DONE -> stringResource(R.string.history_calendar_status_done_format, dateLabel)
        HistoryDayStatus.HEALED -> stringResource(R.string.history_calendar_status_healed_format, dateLabel)
        else -> stringResource(R.string.history_calendar_status_not_done_format, dateLabel)
    }
}
