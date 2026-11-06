package com.pirxhio.affirmity.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.*
import com.pirxhio.affirmity.ui.theme.*
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
        ActivityLegend()
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
                ActivityDayDetails(loaded.days[selected], locale)
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
    val affirmed = day?.let { it.activity.viewedCount > 0 || it.completion?.affirmationDone == true } == true
    val meditated = day?.let { it.activity.sessions.isNotEmpty() || it.completion?.meditationDone == true } == true
    val affirmedLabel = if (affirmed) stringResource(R.string.progress_affirmations_label) else ""
    val meditatedLabel = if (meditated) stringResource(R.string.progress_meditation_label) else ""
    val mood = day?.mood?.let { stringResource(R.string.activity_mood_value, it.moodValue) }.orEmpty()
    val colors = MaterialTheme.colorScheme
    val dots = activityDotColors()
    // Selection is a soft brand tint plus ring (not a solid fill) so the three activity dots keep their contrast.
    Box(Modifier.size(CalendarGridLayout.CellSize).clip(CircleShape)
        .background(if (selectedDay) colors.primary.copy(alpha = 0.12f) else colors.surface)
        .then(if (selectedDay) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier)
        .clickable(enabled = enabled, role = Role.Button, onClick = onSelect)
        .semantics(mergeDescendants = true) {
            contentDescription = listOf(date, todayLabel, selectedLabel, affirmedLabel, meditatedLabel, mood)
                .filter { it.isNotEmpty() }.joinToString(", ")
            selected = selectedDay
        }) {
        // Number and dot row sit at fixed offsets so every day number shares one baseline, dots or not.
        Text(cell.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium,
            color = when { today -> colors.primary; enabled -> colors.onSurface; else -> colors.onSurface.copy(alpha = 0.38f) },
            fontWeight = if (today) FontWeight.Bold else null,
            modifier = Modifier.align(Alignment.Center).offset(y = (-4).dp))
        Row(Modifier.align(Alignment.Center).offset(y = 11.dp), horizontalArrangement = Arrangement.spacedBy(ActivityDotGap)) {
            ActivityDot(dots.affirmation, affirmed)
            ActivityDot(dots.meditation, meditated)
            ActivityDot(dots.mood, day?.mood != null)
        }
    }
}

private val ActivityDotSize = 5.dp
private val ActivityDotGap = 3.dp

private data class ActivityDotColors(val affirmation: Color, val meditation: Color, val mood: Color)

@Composable
private fun activityDotColors(): ActivityDotColors =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) ActivityDotColors(ActivityAffirmationDark, ActivityMeditationDark, ActivityMoodDark)
    else ActivityDotColors(ActivityAffirmationLight, ActivityMeditationLight, ActivityMoodLight)

/** Each activity keeps its own slot, so position carries the meaning as well as hue. */
@Composable
private fun ActivityDot(color: Color, visible: Boolean) {
    Box(Modifier.size(ActivityDotSize).then(if (visible) Modifier.background(color, CircleShape) else Modifier))
}

@Composable
private fun ActivityLegend() {
    val dots = activityDotColors()
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        listOf(dots.affirmation to R.string.progress_affirmations_label, dots.meditation to R.string.progress_meditation_label,
            dots.mood to R.string.nav_mood_label).forEach { (color, label) ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActivityDayDetails(day: ActivityCalendarDay?, locale: Locale) {
    val colors = MaterialTheme.colorScheme
    val dots = activityDotColors()
    val activity = day?.activity
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 4.dp)) {
            val viewed = activity?.viewedCount ?: 0
            ActivityDetailRow(dots.affirmation, stringResource(R.string.progress_affirmations_label), when {
                viewed > 0 -> pluralStringResource(R.plurals.activity_viewed_value, viewed, viewed)
                day?.completion?.affirmationDone == true -> stringResource(R.string.activity_goal_met)
                else -> null
            })
            HorizontalDivider(color = colors.outlineVariant)
            val sessions = activity?.sessions.orEmpty()
            ActivityDetailRow(dots.meditation, stringResource(R.string.progress_meditation_label), when {
                sessions.isNotEmpty() -> durationLabel(activity?.activeSeconds ?: 0L)
                day?.completion?.meditationDone == true -> stringResource(R.string.activity_goal_met)
                else -> null
            })
            sessions.forEach { session ->
                val time = DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { timeZone = TimeZone.getTimeZone(session.zoneId) }
                    .format(java.util.Date(session.occurredAtMillis))
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, bottom = 8.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(requireNotNull(session.nameSnapshot), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
                        modifier = Modifier.weight(1f).padding(end = 12.dp))
                    Text(stringResource(R.string.activity_session_detail, time, durationLabel(session.activeSeconds)),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            ActivityDetailRow(dots.mood, stringResource(R.string.nav_mood_label),
                day?.mood?.let { stringResource(R.string.activity_mood_score, it.moodValue) })
            day?.mood?.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
            }
        }
        if (day?.healed == true) Text(stringResource(R.string.activity_healed), style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant)
        if (day == null || day.activity.coverage == ActivityCoverage.UNKNOWN) {
            val nothing = day == null || (day.activity.sessions.isEmpty() && day.activity.viewedCount == 0 &&
                day.completion?.affirmationDone != true && day.completion?.meditationDone != true)
            Text(stringResource(if (nothing) R.string.activity_unknown else R.string.activity_partial),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/** One activity line: the calendar's dot colour, its name, and the day's value (or "not recorded"). */
@Composable
private fun ActivityDetailRow(color: Color, label: String, value: String?) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        else {
            // A quiet dash reads cleaner than words; TalkBack still announces "not recorded".
            val notRecorded = stringResource(R.string.activity_not_recorded)
            Text("\u2013", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = notRecorded })
        }
    }
}

@Composable
private fun durationLabel(seconds: Long): String = stringResource(R.string.activity_duration_format, seconds / 60, seconds % 60)
