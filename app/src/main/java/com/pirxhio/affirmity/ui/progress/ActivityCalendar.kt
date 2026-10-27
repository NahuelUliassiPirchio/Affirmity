package com.pirxhio.affirmity.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Embedded daily practice calendar. Seven equal-weight columns of 48dp touch targets fill the available width and
 * the grid scrolls horizontally only when 7x48dp cannot fit.
 *
 * [gridBleed] lets the grid overflow the host's horizontal padding by this much on each side to avoid scrolling.
 * Contract: the host must have at least this much horizontal padding on each side and must not clip its content.
 */
@Composable
internal fun ActivityCalendar(observeMonth: (Long, Long) -> Flow<ActivityMonthState>, saveError: Throwable? = null, onRetry: () -> Unit = {},
    gridBleed: Dp = 0.dp) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    var refresh by remember { mutableIntStateOf(0) }
    var boundary by remember { mutableStateOf(DayClock.epochDay() to TimeZone.getDefault().id) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            boundary = DayClock.epochDay() to TimeZone.getDefault().id
            refresh++
        } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { while (true) {
        delay(30_000)
        val current = DayClock.epochDay() to TimeZone.getDefault().id
        if (current != boundary) { boundary = current; refresh++ }
    } }
    val today = boundary.first
    val now = Calendar.getInstance()
    val currentMonth = now.get(Calendar.YEAR) * 12 + now.get(Calendar.MONTH)
    var month by rememberSaveable { mutableIntStateOf(currentMonth) }
    val monthLayout = remember(month, boundary, locale) { HistoryMonthLayout.of(month.floorDiv(12), month.mod(12), Calendar.getInstance(locale)) }
    var selected by rememberSaveable(month) { mutableLongStateOf(if (month == currentMonth) today else monthLayout.cells.first().epochDay) }
    val monthFlow = remember(month, refresh, boundary, observeMonth) {
        observeMonth(monthLayout.cells.first().epochDay, monthLayout.cells.last().epochDay)
    }
    val state by monthFlow.collectAsState(initial = ActivityMonthState.Loading)
    val monthDate = remember(month, boundary) { DayClock.calendarForEpochDay(monthLayout.cells.first().epochDay).time }
    val monthLabel = SimpleDateFormat("LLLL yyyy", locale).format(monthDate)
    val ready = state as? ActivityMonthState.Ready
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.activity_title), style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { month-- }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.activity_previous_month))
            }
            Text(monthLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            IconButton(onClick = { month++ }, enabled = month < currentMonth, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.activity_next_month))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fits = CalendarGridLayout.fits(maxWidth, gridBleed)
            val scroll = rememberScrollState()
            val scope = rememberCoroutineScope()
            val narrowLabels = CalendarGridLayout.usesNarrowWeekdays(LocalDensity.current.fontScale)
            val weekdays = remember(locale, narrowLabels) { weekdayLabels(locale, narrowLabels) }
            Column(verticalArrangement = Arrangement.spacedBy(CalendarGridLayout.SectionSpacing)) {
                if (!fits) {
                    Text(stringResource(R.string.activity_scroll_hint), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(CalendarGridLayout.SectionSpacing)) {
                        TextButton(onClick = { scope.launch { scroll.animateScrollTo(0) } }) { Text(stringResource(R.string.activity_scroll_start)) }
                        TextButton(onClick = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } }) { Text(stringResource(R.string.activity_scroll_end)) }
                    }
                }
                // Equal-weight columns fill the available width; below 7x48dp the grid keeps its minimum width and scrolls.
                val gridModifier = if (fits) Modifier.fillMaxWidth().bleedToMinWidth()
                else Modifier.horizontalScroll(scroll).width(CalendarGridLayout.MinWidth)
                Column(gridModifier, verticalArrangement = Arrangement.spacedBy(CalendarGridLayout.RowSpacing)) {
                    Row(Modifier.fillMaxWidth()) {
                        weekdays.forEachIndexed { offset, weekday ->
                            Box(Modifier.weight(1f).testTag("calendar-weekday-$offset"), contentAlignment = Alignment.Center) {
                                Text(weekday.short, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false,
                                    modifier = Modifier.semantics { contentDescription = weekday.full })
                            }
                        }
                    }
                    repeat(6) { week ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { weekday ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    val cell = monthLayout.cells.getOrNull(week * 7 + weekday - monthLayout.leadingBlanks)
                                    if (cell == null) Spacer(Modifier.size(CalendarGridLayout.CellSize))
                                    else ActivityDateCell(cell, ready?.days?.get(cell.epochDay), cell.epochDay == today,
                                        cell.epochDay == selected, cell.epochDay <= today, locale) { selected = cell.epochDay }
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Text(DateFormat.getDateInstance(DateFormat.FULL, locale).format(DayClock.calendarForEpochDay(selected).time),
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (saveError != null) Text(stringResource(R.string.activity_save_error), color = MaterialTheme.colorScheme.error)
        when (val loaded = state) {
            ActivityMonthState.Loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.activity_loading))
            }
            is ActivityMonthState.Error -> {
                Text(stringResource(R.string.activity_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { onRetry(); refresh++ }) { Text(stringResource(R.string.activity_retry)) }
            }
            is ActivityMonthState.Ready -> {
                if (loaded.pendingCount > 0) Text(stringResource(R.string.activity_pending), style = MaterialTheme.typography.bodySmall)
                loaded.days[selected]?.let { ActivityDayDetails(it, locale) }
                    ?: Text(stringResource(R.string.activity_unknown))
            }
        }
    }
}

/** Widens the grid to [CalendarGridLayout.MinWidth] when the host is narrower, centred over the host's padding. */
private fun Modifier.bleedToMinWidth(): Modifier = layout { measurable, constraints ->
    val target = maxOf(constraints.maxWidth, CalendarGridLayout.MinWidth.roundToPx())
    val placeable = measurable.measure(Constraints.fixedWidth(target))
    layout(constraints.maxWidth, placeable.height) { placeable.place((constraints.maxWidth - target) / 2, 0) }
}

@Composable
private fun ActivityDateCell(cell: HistoryLayoutCell, day: ActivityCalendarDay?, today: Boolean,
    selectedDay: Boolean, enabled: Boolean, locale: Locale, onSelect: () -> Unit) {
    val date = DateFormat.getDateInstance(DateFormat.FULL, locale).format(DayClock.calendarForEpochDay(cell.epochDay).time)
    val todayLabel = if (today) stringResource(R.string.activity_today) else ""
    val selectedLabel = if (selectedDay) stringResource(R.string.activity_selected) else ""
    val mood = day?.mood?.let { stringResource(R.string.activity_mood_value, it.moodValue) }.orEmpty()
    val hasRecord = day?.let { it.mood != null || it.completion != null || it.healed || it.activity.viewedCount > 0 || it.activity.sessions.isNotEmpty() } == true
    val recordLabel = if (hasRecord) stringResource(R.string.activity_has_records) else ""
    val shape = CircleShape
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(CalendarGridLayout.CellSize).background(if (selectedDay) colors.secondaryContainer else colors.surface, shape)
        .then(if (today) Modifier.border(1.dp, colors.primary, shape) else Modifier)
        .clickable(enabled = enabled, role = Role.Button, onClick = onSelect)
        .semantics(mergeDescendants = true) {
            contentDescription = listOf(date, todayLabel, selectedLabel, mood, recordLabel).filter { it.isNotEmpty() }.joinToString(", ")
            selected = selectedDay
        }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(cell.dayOfMonth.toString(), color = if (selectedDay) colors.onSecondaryContainer else colors.onSurface,
                style = MaterialTheme.typography.bodyMedium)
            if (hasRecord) Icon(Icons.Filled.Circle, null, Modifier.size(6.dp), tint = if (selectedDay) colors.onSecondaryContainer else colors.primary)
        }
    }
}

@Composable
private fun ActivityDayDetails(day: ActivityCalendarDay, locale: Locale) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(day.mood?.let { stringResource(R.string.activity_mood_value, it.moodValue) }
            ?: stringResource(R.string.activity_no_mood), style = MaterialTheme.typography.bodyLarge)
        day.mood?.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (day.healed) Text(stringResource(R.string.activity_healed), style = MaterialTheme.typography.bodySmall)
        if (day.completion?.meditationDone == true && day.activity.sessions.isEmpty()) Text(stringResource(R.string.activity_legacy_meditation))
        if (day.completion?.affirmationDone == true && day.activity.viewedCount == 0) Text(stringResource(R.string.activity_legacy_affirmations))
        val activity = day.activity
        if (activity.coverage == ActivityCoverage.UNKNOWN) Text(stringResource(
            if (activity.sessions.isEmpty() && activity.viewedCount == 0) R.string.activity_unknown else R.string.activity_partial),
            style = MaterialTheme.typography.bodySmall)
        if (activity.confirmedEmpty) Text(stringResource(R.string.activity_empty))
        if (activity.coverage != ActivityCoverage.UNKNOWN || activity.viewedCount > 0) Text(stringResource(R.string.activity_viewed_count, activity.viewedCount))
        if (activity.coverage != ActivityCoverage.UNKNOWN || activity.sessions.isNotEmpty()) {
            Text(stringResource(R.string.activity_total_duration, durationLabel(activity.activeSeconds)), style = MaterialTheme.typography.titleSmall)
        }
        activity.sessions.forEach { session ->
            val time = DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { timeZone = TimeZone.getTimeZone(session.zoneId) }.format(java.util.Date(session.occurredAtMillis))
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                Text(requireNotNull(session.nameSnapshot), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.activity_session_detail, time, durationLabel(session.activeSeconds)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun durationLabel(seconds: Long): String = stringResource(R.string.activity_duration_format, seconds / 60, seconds % 60)
