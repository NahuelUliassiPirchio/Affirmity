package com.pirxhio.affirmity.ui.progress

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Pure layout rules for the activity calendar grid (JVM-testable). */
internal object CalendarGridLayout {
    val CellSize: Dp = 48.dp
    private const val Columns = 7

    /** Vertical gap between week rows. */
    val RowSpacing: Dp = 4.dp

    /** Gap between the scroll hint/actions and the grid. */
    val SectionSpacing: Dp = 8.dp

    // At about 1.3x, 12sp labelMedium short names ("Mon") approach the 48dp column width; below it they fit.
    private const val NarrowWeekdayFontScale = 1.3f

    /** Seven touch-target-sized columns with no gaps; the columns flex wider when space allows. */
    val MinWidth: Dp = CellSize * Columns

    /**
     * True when seven 48dp columns fit without horizontal scrolling. [bleed] is the extra width the
     * grid may borrow from the host's page padding on each side (e.g. 360dp phones).
     */
    fun fits(available: Dp, bleed: Dp = 0.dp): Boolean = available + bleed * 2 >= MinWidth

    /** Narrow weekday labels once text is scaled enough that short names would not fit a column. */
    fun usesNarrowWeekdays(fontScale: Float): Boolean = fontScale >= NarrowWeekdayFontScale
}

/** Visible weekday header text plus the full day name used for accessibility. */
internal class WeekdayLabel(val short: String, val full: String)

/**
 * Seven headers starting at [locale]'s first day of week, matching `Calendar.getInstance(locale)`.
 * Uses format-style "EEE"/"EEEEE" rather than the standalone "ccc" because Android's SimpleDateFormat
 * does not document the 'c' pattern letter on minSdk 24; the abbreviations are identical for en and es.
 */
internal fun weekdayLabels(locale: Locale, narrow: Boolean): List<WeekdayLabel> {
    val firstWeekday = Calendar.getInstance(locale).firstDayOfWeek
    val shortFormat = SimpleDateFormat(if (narrow) "EEEEE" else "EEE", locale)
    val fullFormat = SimpleDateFormat("EEEE", locale)
    return List(7) { offset ->
        val date = Calendar.getInstance(locale).apply { set(Calendar.DAY_OF_WEEK, (firstWeekday - 1 + offset) % 7 + 1) }.time
        WeekdayLabel(shortFormat.format(date), fullFormat.format(date))
    }
}
