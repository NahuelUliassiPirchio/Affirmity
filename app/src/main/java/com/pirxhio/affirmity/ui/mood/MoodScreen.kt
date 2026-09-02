package com.pirxhio.affirmity.ui.mood

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.DailyMoodStats
import com.pirxhio.affirmity.data.DayClock
import com.pirxhio.affirmity.data.MOOD_MAX
import com.pirxhio.affirmity.data.MoodDistribution
import com.pirxhio.affirmity.data.MoodTrendPoint
import com.pirxhio.affirmity.data.local.DailyMoodEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class ResumenPeriod { SEMANA, MES }

@Composable
fun MoodScreen(
    moodEntries: List<DailyMoodEntity>,
    onSaveMood: (epochDay: Long, moodValue: Int, note: String?) -> Unit,
    initialMoodValue: Int? = null,
    openInitialPicker: Boolean = false,
    initialMoodEventKey: Int = 0,
    onInitialMoodConsumed: () -> Unit = {},
    onInitialPickerConsumed: () -> Unit = {},
) {
    // Not `remember`-keyed: `moodEntries` is a SnapshotStateList mutated in place (clear + addAll),
    // so its reference never changes and a remember(moodEntries) key would never invalidate,
    // freezing the calendar on whatever data existed at first composition.
    val moodByDay = moodEntries.associateBy { it.epochDay }
    val todayEpochDay = remember { DayClock.epochDay() }

    // Resolved per composition from LocalConfiguration, not a process-lifetime singleton — a
    // locale switch recreates the Activity, which produces a new LocalConfiguration and therefore
    // re-runs this composable with the new locale (D7).
    val locale = LocalConfiguration.current.locales[0]
    val monthFormatPattern = stringResource(R.string.mood_month_format)
    val dayLabelFormatPattern = stringResource(R.string.mood_day_label_format)
    val todayLabelTemplate = stringResource(R.string.mood_day_label_today)
    val monthFormatter = remember(monthFormatPattern, locale) { SimpleDateFormat(monthFormatPattern, locale) }
    val dayLabelFormatter = remember(dayLabelFormatPattern, locale) { SimpleDateFormat(dayLabelFormatPattern, locale) }

    fun dayLabel(calendar: Calendar, epochDay: Long): String =
        formatDayLabel(calendar, epochDay, todayEpochDay, dayLabelFormatter, todayLabelTemplate)

    var visibleMonth by remember {
        mutableStateOf(
            (Calendar.getInstance().clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        )
    }
    var period by remember { mutableStateOf(ResumenPeriod.SEMANA) }
    var selectedDay by remember { mutableStateOf<SelectedDay?>(null) }

    // Mood notification actions land here with a pre-picked value; tapping the notification body
    // opens the same sheet with no synthetic selection so all five choices remain explicit.
    LaunchedEffect(initialMoodValue, openInitialPicker, initialMoodEventKey) {
        if (initialMoodValue != null || openInitialPicker) {
            selectedDay = SelectedDay(
                epochDay = todayEpochDay,
                label = dayLabel(Calendar.getInstance(), todayEpochDay),
                moodValue = initialMoodValue,
                note = moodByDay[todayEpochDay]?.note,
            )
            if (initialMoodValue != null) onInitialMoodConsumed()
            if (openInitialPicker) onInitialPickerConsumed()
        }
    }

    val monthDays = remember(visibleMonth) { monthDaysWithEpoch(visibleMonth) }
    val monthEntries = monthDays.mapNotNull { moodByDay[it.epochDay] }
    val monthAverage = DailyMoodStats.average(monthEntries)
    val streakDays = DailyMoodStats.streakOf(moodEntries, todayEpochDay)

    val periodTrend = when (period) {
        ResumenPeriod.SEMANA -> DailyMoodStats.trend(moodEntries, todayEpochDay - 6, days = 7)
        ResumenPeriod.MES -> DailyMoodStats.trend(moodEntries, monthDays.first().epochDay, days = monthDays.size)
    }
    val periodEntries = when (period) {
        ResumenPeriod.SEMANA -> periodTrend.mapNotNull { point -> moodByDay[point.epochDay] }
        ResumenPeriod.MES -> monthEntries
    }
    val periodDistribution = DailyMoodStats.distribution(periodEntries)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 24.dp, top = 40.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.mood_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.mood_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        item {
            MoodHeaderControls(
                visibleMonth = visibleMonth,
                average = monthAverage,
                streakDays = streakDays,
                monthFormatter = monthFormatter,
                onPrevMonth = { visibleMonth = shiftMonth(visibleMonth, -1) },
                onNextMonth = { visibleMonth = shiftMonth(visibleMonth, 1) },
            )
        }

        item {
            MoodCalendarGrid(
                monthDays = monthDays,
                moodByDay = moodByDay,
                todayEpochDay = todayEpochDay,
                onDayClick = { day ->
                    val entry = moodByDay[day.epochDay]
                    selectedDay = SelectedDay(
                        epochDay = day.epochDay,
                        label = dayLabel(day.calendar, day.epochDay),
                        moodValue = entry?.moodValue,
                        note = entry?.note,
                    )
                },
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.mood_resumen_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ResumenToggle(period = period, onPeriodChange = { period = it })
                }
                TrendChart(points = periodTrend)
                MoodDistributionBars(distribution = periodDistribution)
            }
        }
    }

    selectedDay?.let { day ->
        val canLog = day.epochDay <= todayEpochDay
        if (canLog) {
            MoodDayDetailSheet(
                dayLabel = day.label,
                initialMoodValue = day.moodValue,
                selectionEventKey = initialMoodEventKey,
                initialNote = day.note,
                onDismiss = { selectedDay = null },
                onSave = { moodValue, note ->
                    onSaveMood(day.epochDay, moodValue, note)
                    selectedDay = null
                },
            )
        } else {
            selectedDay = null
        }
    }
}

private data class SelectedDay(
    val epochDay: Long,
    val label: String,
    val moodValue: Int?,
    val note: String?,
)

private data class MonthDay(val dayOfMonth: Int, val epochDay: Long, val calendar: Calendar)

private fun monthDaysWithEpoch(visibleMonth: Calendar): List<MonthDay> {
    val daysInMonth = visibleMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
    return (1..daysInMonth).map { day ->
        val cell = (visibleMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
        MonthDay(dayOfMonth = day, epochDay = DayClock.epochDay(cell), calendar = cell)
    }
}

private fun shiftMonth(visibleMonth: Calendar, delta: Int): Calendar =
    (visibleMonth.clone() as Calendar).apply { add(Calendar.MONTH, delta) }

/**
 * Builds the resolved-locale day label. Takes an already-constructed [dayFormatter] and
 * [todayLabelTemplate] (a `%1$s` format string, e.g. "Hoy, %1$s") because this function is called
 * from non-composable contexts (event-handler lambdas, `LaunchedEffect` bodies) — the caller
 * resolves both from `stringResource`/`LocalConfiguration` inside the composable and passes them
 * down (D7). `SimpleDateFormat` is not thread-safe, so a fresh instance per composition is also a
 * correctness win, not just a localization one.
 */
private fun formatDayLabel(
    calendar: Calendar,
    epochDay: Long,
    todayEpochDay: Long,
    dayFormatter: SimpleDateFormat,
    todayLabelTemplate: String,
): String {
    val formatted = dayFormatter.format(calendar.time).replaceFirstChar { it.uppercase() }
    return if (epochDay == todayEpochDay) String.format(todayLabelTemplate, formatted) else formatted
}

@Composable
private fun MoodHeaderControls(
    visibleMonth: Calendar,
    average: Double?,
    streakDays: Int,
    monthFormatter: SimpleDateFormat,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.mood_prev_month_content_description))
            }
            Text(
                text = monthFormatter.format(visibleMonth.time).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.mood_next_month_content_description))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val averageText = average?.let { "%.1f".format(it) } ?: stringResource(R.string.mood_average_empty)
            StatChip(text = stringResource(R.string.mood_average_format, averageText))
            StatChip(text = stringResource(R.string.mood_streak_format, streakDays))
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MoodCalendarGrid(
    monthDays: List<MonthDay>,
    moodByDay: Map<Long, DailyMoodEntity>,
    todayEpochDay: Long,
    onDayClick: (MonthDay) -> Unit,
) {
    // Calendar.DAY_OF_WEEK: Sun=1..Sat=7; convert to a Monday-first leading-blank count.
    val leadingBlanks = ((monthDays.first().calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7)
    val cells: List<MonthDay?> = List(leadingBlanks) { null } + monthDays
    val weekdayHeaders = stringArrayResource(R.array.mood_weekday_headers)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weekdayHeaders.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                            if (day != null) {
                                MoodDayCell(
                                    day = day,
                                    entry = moodByDay[day.epochDay],
                                    isToday = day.epochDay == todayEpochDay,
                                    isFuture = day.epochDay > todayEpochDay,
                                    onClick = { onDayClick(day) },
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) { Box(modifier = Modifier.weight(1f).aspectRatio(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MoodDayCell(
    day: MonthDay,
    entry: DailyMoodEntity?,
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
) {
    val fillAlpha = entry?.let { it.moodValue / MOOD_MAX.toFloat() * 0.7f + 0.1f } ?: 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(enabled = !isFuture, onClick = onClick)
            .background(
                color = if (entry != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = fillAlpha)
                else MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(8.dp),
            )
            .then(
                if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isFuture) MaterialTheme.colorScheme.outline.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp).align(Alignment.TopStart),
        )
        if (entry != null) {
            Text(text = moodEmoji(entry.moodValue), fontSize = 16.sp)
        }
    }
}

@Composable
private fun ResumenToggle(period: ResumenPeriod, onPeriodChange: (ResumenPeriod) -> Unit) {
    Row(
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(2.dp),
    ) {
        ResumenPeriod.entries.forEach { candidate ->
            val selected = candidate == period
            Box(
                modifier = Modifier
                    .clickable { onPeriodChange(candidate) }
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (candidate == ResumenPeriod.SEMANA) {
                        stringResource(R.string.mood_period_week)
                    } else {
                        stringResource(R.string.mood_period_month)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun TrendChart(points: List<MoodTrendPoint>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.mood_trend_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            val lineColor = MaterialTheme.colorScheme.primaryContainer
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(top = 12.dp),
            ) {
                if (points.size < 2) return@Canvas
                val stepX = size.width / (points.size - 1)
                fun yFor(moodValue: Int): Float {
                    val fraction = (moodValue - 1) / (MOOD_MAX - 1).toFloat()
                    return size.height * (1f - fraction)
                }
                var previous: Offset? = null
                points.forEachIndexed { index, point ->
                    val value = point.moodValue
                    if (value == null) {
                        previous = null
                        return@forEachIndexed
                    }
                    val current = Offset(x = index * stepX, y = yFor(value))
                    previous?.let { start ->
                        drawLine(color = lineColor, start = start, end = current, strokeWidth = 4f, cap = StrokeCap.Round)
                    }
                    drawCircle(color = lineColor, radius = 5f, center = current)
                    previous = current
                }
            }
        }
    }
}

@Composable
private fun MoodDistributionBars(distribution: MoodDistribution) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.mood_distribution_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            // Highest mood value first (5 down to 1), matching the calendar's best-to-worst reading order.
            (MOOD_MAX downTo 1).forEach { value ->
                val percent = distribution.percentByValue.getOrElse(value - 1) { 0 }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = moodEmoji(value), fontSize = 18.sp, modifier = Modifier.size(24.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percent / 100f)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp)),
                        )
                    }
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(width = 36.dp, height = 16.dp),
                    )
                }
            }
        }
    }
}
