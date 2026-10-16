package com.pirxhio.affirmity.widget

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pure sizing and accessibility logic for the weekly tracker widget. Kept free of Glance and
 * Compose types so the tier boundaries, cell scaling and descriptions are JVM-unit-testable;
 * [WeeklyTrackerContent] only maps the result onto Glance primitives. All lengths are dp.
 */
internal enum class WidgetTier { Strip, Regular, Tall }

/** Where the streak counter sits; [None] when the streak is hidden (zero or no history). */
internal enum class StreakPlacement { None, LeadingRow, BesideGrid, Header }

internal data class TrackerLayout(
    val tier: WidgetTier,
    val showLetters: Boolean,
    val padding: Float,
    val cellDiameter: Int,
    val letterSizeSp: Int,
    val legendSizeSp: Int,
    /** False when the beside-grid legend cannot fit its height even at the minimum font size. */
    val legendVisible: Boolean,
    val streakPlacement: StreakPlacement,
    /** Estimated height of the column beside the grid (strip/regular); 0 in the tall tier. */
    val sideColumnHeight: Float,
    /** Height reserved above the grid in the tall tier (wrap-content in the UI); 0 otherwise. */
    val tallHeaderHeight: Float,
    /** Width reserved for a leading streak (three digits plus flame); 0 unless it leads the row. */
    val leadingStreakWidth: Float,
)

internal const val TALL_STREAK_SP = 36f
internal const val TALL_CAPTION_SP = 12f
internal const val TALL_LEGEND_GAP_DP = 5f
internal const val TALL_LEGEND_TOP_PADDING_DP = 4f

internal const val STRIP_STREAK_FLAME_DP = 16f
internal const val STRIP_STREAK_SP = 16f
internal const val STREAK_FLAME_GAP_DP = 3f

/** Regular tier streak row (beside the grid) and the gap that separates it from the legend. */
internal const val REGULAR_STREAK_SP = 18f
internal const val SIDE_COLUMN_GAP_DP = 4f
internal const val LEGEND_ROW_GAP_DP = 3f

private const val LETTERS_GAP_DP = 4f
private const val STRIP_LETTERS_MAX_SP = 11f
private const val LETTERS_MAX_SP = 13f
private const val MIN_LEGEND_SP = 9
private const val STREAK_WIDTH_SLACK_DP = 4f
private const val LINE_HEIGHT_FACTOR = 1.2f
private const val DIGIT_WIDTH_EM = 0.6f
private const val STREAK_DIGITS_RESERVED = 3

/** Height of one text line, given its size already converted to dp. */
private fun lineHeight(dp: Float) = dp * LINE_HEIGHT_FACTOR

/**
 * Height of the tall header's content: the streak number plus its caption. [spToDp] converts a
 * text size in sp to dp for the current font scale (identity by default, which suits tests).
 */
internal fun tallHeaderContentHeightDp(spToDp: (Float) -> Float = { it }): Float =
    lineHeight(spToDp(TALL_STREAK_SP)) + lineHeight(spToDp(TALL_CAPTION_SP))

internal fun stripStreakContentWidthDp(digits: Int, spToDp: (Float) -> Float = { it }): Float =
    STRIP_STREAK_FLAME_DP + STREAK_FLAME_GAP_DP + kotlin.math.ceil(digits * DIGIT_WIDTH_EM * spToDp(STRIP_STREAK_SP))

private fun legendHeightDp(sp: Int, gap: Float, spToDp: (Float) -> Float) =
    2 * lineHeight(spToDp(sp.toFloat())) + gap

private const val REGULAR_MIN_HEIGHT = 92f
private const val TALL_MIN_HEIGHT = 150f
private const val LETTERS_MIN_HEIGHT = 74f
private const val COMPACT_PADDING_BELOW = 76f
private const val NARROW_BELOW_WIDTH = 280f
private const val MIN_CELL_DIAMETER = 10f

/**
 * Room the today ring needs around a cell: the ring is drawn inside its own column (Glance clips
 * children to their parent), so it costs this much of both the column width and the row height.
 */
private const val RING_ALLOWANCE = 6f

internal fun tierForHeight(heightDp: Float): WidgetTier = when {
    heightDp < REGULAR_MIN_HEIGHT -> WidgetTier.Strip
    heightDp < TALL_MIN_HEIGHT -> WidgetTier.Regular
    else -> WidgetTier.Tall
}

/**
 * Glance applies text sizes in sp, so every text-dependent reservation (line heights, digit
 * widths, legend width) is derived from [spToDp] rather than assumed at font scale 1. The widget
 * builds it from the real display metrics (see `rememberSpToDp` in the widget file), which also
 * covers Android 14+ non-linear font scaling.
 *
 * When large fonts leave no room, text is never clipped to make space: the cells shrink first
 * (down to [MIN_CELL_DIAMETER]), then the weekday letters are dropped, the legend font steps down
 * to [MIN_LEGEND_SP], a regular-tier streak moves to lead the row, and a tall widget that cannot
 * fit its header falls back to the regular layout. As a last step the beside-grid legend is hidden
 * when two lines cannot fit even at the floor size, and its width and gap return to the grid.
 */
internal fun computeTrackerLayout(
    widthDp: Float,
    heightDp: Float,
    streakVisible: Boolean,
    spToDp: (Float) -> Float = { it },
): TrackerLayout {
    val nominal = tierForHeight(heightDp)
    val first = buildLayout(nominal, widthDp, heightDp, streakVisible, spToDp)
    if (nominal == WidgetTier.Tall && !first.cellsFit) {
        return buildLayout(WidgetTier.Regular, widthDp, heightDp, streakVisible, spToDp).layout
    }
    return first.layout
}

private class BuiltLayout(val layout: TrackerLayout, val cellsFit: Boolean)

private fun buildLayout(
    tier: WidgetTier,
    widthDp: Float,
    heightDp: Float,
    streakVisible: Boolean,
    spToDp: (Float) -> Float,
): BuiltLayout {
    val narrow = widthDp < NARROW_BELOW_WIDTH
    val pad = if (heightDp < COMPACT_PADDING_BELOW) 10f else 14f
    val contentHeight = heightDp - 2 * pad

    val nominalLegend = when {
        tier == WidgetTier.Strip && narrow -> 11
        tier == WidgetTier.Strip || narrow -> 12
        else -> 13
    }
    // The header legend (tall) keeps its size; beside-the-grid legends step down until they fit.
    var legendSize = nominalLegend
    if (tier != WidgetTier.Tall) {
        while (legendSize > MIN_LEGEND_SP && legendHeightDp(legendSize, LEGEND_ROW_GAP_DP, spToDp) > contentHeight) legendSize--
    }
    // Hidden (rather than clipped) when two lines cannot fit even at the floor size.
    val legendVisible = tier == WidgetTier.Tall ||
        legendHeightDp(legendSize, LEGEND_ROW_GAP_DP, spToDp) <= contentHeight
    val legendHeight = if (legendVisible) legendHeightDp(legendSize, LEGEND_ROW_GAP_DP, spToDp) else 0f
    val streakLine = lineHeight(spToDp(REGULAR_STREAK_SP))
    val besideColumnWithStreak = streakLine + (if (legendVisible) SIDE_COLUMN_GAP_DP + legendHeight else 0f)

    val placement = when {
        !streakVisible -> StreakPlacement.None
        tier == WidgetTier.Strip -> StreakPlacement.LeadingRow
        tier == WidgetTier.Regular ->
            if (besideColumnWithStreak <= contentHeight) StreakPlacement.BesideGrid else StreakPlacement.LeadingRow
        else -> StreakPlacement.Header
    }
    val leadingStreakWidth =
        if (placement == StreakPlacement.LeadingRow) {
            stripStreakContentWidthDp(STREAK_DIGITS_RESERVED, spToDp) + STREAK_WIDTH_SLACK_DP
        } else {
            0f
        }

    val tallHeader = if (tier == WidgetTier.Tall) {
        val streakColumn = if (streakVisible) tallHeaderContentHeightDp(spToDp) else 0f
        val legendColumn = TALL_LEGEND_TOP_PADDING_DP + legendHeightDp(legendSize, TALL_LEGEND_GAP_DP, spToDp)
        maxOf(streakColumn, legendColumn)
    } else {
        0f
    }

    // Legend width is the design's estimate at its nominal size, scaled with the text.
    val legendWidth = (if (tier == WidgetTier.Strip) (if (narrow) 60f else 78f) else (if (narrow) 70f else 86f)) *
        spToDp(legendSize.toFloat()) / nominalLegend
    // The side column's width: the legend, or just the streak row when the legend is hidden.
    val sideColumnWidth = when {
        legendVisible -> legendWidth
        placement == StreakPlacement.BesideGrid ->
            STRIP_STREAK_FLAME_DP + STREAK_FLAME_GAP_DP +
                kotlin.math.ceil(STREAK_DIGITS_RESERVED * DIGIT_WIDTH_EM * spToDp(REGULAR_STREAK_SP))
        else -> 0f
    }
    val gridWidth = if (tier == WidgetTier.Tall) {
        widthDp - 2 * pad
    } else {
        widthDp - 2 * pad - (if (sideColumnWidth > 0f) sideColumnWidth + 12f else 0f) -
            (if (leadingStreakWidth > 0f) leadingStreakWidth + 12f else 0f)
    }
    val cap = when (tier) {
        WidgetTier.Strip -> 34f
        WidgetTier.Regular -> 40f
        WidgetTier.Tall -> 48f
    }

    val lettersReserve = LETTERS_GAP_DP +
        lineHeight(spToDp(if (tier == WidgetTier.Strip) STRIP_LETTERS_MAX_SP else LETTERS_MAX_SP))
    val aboveGrid = if (tier == WidgetTier.Tall) tallHeader + 12f else 0f
    fun availHeight(withLetters: Boolean) =
        contentHeight - aboveGrid - (if (withLetters) lettersReserve else 0f) - RING_ALLOWANCE

    val wantLetters = tier != WidgetTier.Strip || heightDp >= LETTERS_MIN_HEIGHT
    val showLetters = wantLetters && availHeight(true) >= MIN_CELL_DIAMETER
    val availableForCells = availHeight(showLetters)

    val column = gridWidth / 7f
    val diameter = floor(
        maxOf(MIN_CELL_DIAMETER, minOf(column * 0.8f, column - RING_ALLOWANCE, availableForCells, cap)),
    ).toInt()

    val layout = TrackerLayout(
        tier = tier,
        showLetters = showLetters,
        padding = pad,
        cellDiameter = diameter,
        letterSizeSp = (diameter * 0.34f).roundToInt().coerceIn(10, if (tier == WidgetTier.Strip) 11 else 13),
        legendSizeSp = legendSize,
        legendVisible = legendVisible,
        streakPlacement = placement,
        sideColumnHeight = when {
            tier == WidgetTier.Tall -> 0f
            placement == StreakPlacement.BesideGrid -> besideColumnWithStreak
            else -> legendHeight
        },
        tallHeaderHeight = tallHeader,
        leadingStreakWidth = leadingStreakWidth,
    )
    return BuiltLayout(layout, cellsFit = availableForCells >= MIN_CELL_DIAMETER)
}

/** Already-localized fragments used to build the widget's accessibility descriptions. */
internal data class TrackerA11yStrings(
    val bothDone: String,
    val onlyAffirm: String,
    val onlyMeditate: String,
    val nothing: String,
    val healed: String,
    val today: String,
    val streakUnit: String,
    val todayDone: String,
    val todayPending: String,
    val startToday: String,
)

/** "Wednesday: affirmed and meditated"; today is prefixed, a healed day ignores the flags. */
internal fun cellDescription(
    dayName: String,
    isToday: Boolean,
    affirmationDone: Boolean,
    meditationDone: Boolean,
    isHealed: Boolean,
    strings: TrackerA11yStrings,
): String {
    val what = when {
        isHealed -> strings.healed
        affirmationDone && meditationDone -> strings.bothDone
        affirmationDone -> strings.onlyAffirm
        meditationDone -> strings.onlyMeditate
        else -> strings.nothing
    }
    return (if (isToday) "${strings.today}, " else "") + "$dayName: $what"
}

/** Widget-level description: the streak (when there is one) and today's status, or the prompt. */
internal fun widgetDescription(
    hasAny: Boolean,
    streakDays: Int,
    isTodayDone: Boolean,
    strings: TrackerA11yStrings,
): String {
    if (!hasAny) return strings.startToday
    val status = if (isTodayDone) strings.todayDone else strings.todayPending
    return if (streakDays > 0) "$streakDays ${strings.streakUnit}, $status" else status
}
