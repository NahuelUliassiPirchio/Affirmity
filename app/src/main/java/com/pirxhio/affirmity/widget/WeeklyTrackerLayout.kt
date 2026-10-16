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
    val streakPlacement: StreakPlacement,
    /** Estimated height of the column beside the grid (strip/regular); 0 in the tall tier. */
    val sideColumnHeight: Float,
)

/** Tall header row height; must hold the streak number plus its caption (see [tallHeaderContentHeightDp]). */
internal const val TALL_HEADER_HEIGHT_DP = 60f
internal const val TALL_STREAK_SP = 36f
internal const val TALL_CAPTION_SP = 12f

/** Width reserved for the strip's leading streak: flame + gap + up to three digits. */
internal const val STRIP_STREAK_WIDTH_DP = 56f
internal const val STRIP_STREAK_FLAME_DP = 16f
internal const val STRIP_STREAK_SP = 16f
internal const val STREAK_FLAME_GAP_DP = 3f

/** Regular tier streak row (beside the grid) and the gap that separates it from the legend. */
internal const val REGULAR_STREAK_SP = 18f
internal const val SIDE_COLUMN_GAP_DP = 4f
internal const val LEGEND_ROW_GAP_DP = 3f

/** 4dp gap + one letters line (strip letters are capped at 11sp, about 13dp). */
private const val STRIP_LETTERS_RESERVE = 18f
private const val LINE_HEIGHT_FACTOR = 1.2f
private const val DIGIT_WIDTH_EM = 0.6f

private fun lineHeight(sp: Float) = sp * LINE_HEIGHT_FACTOR

internal fun tallHeaderContentHeightDp(): Float = lineHeight(TALL_STREAK_SP) + lineHeight(TALL_CAPTION_SP)

internal fun stripStreakContentWidthDp(digits: Int): Float =
    STRIP_STREAK_FLAME_DP + STREAK_FLAME_GAP_DP + kotlin.math.ceil(digits * DIGIT_WIDTH_EM * STRIP_STREAK_SP)

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

internal fun computeTrackerLayout(widthDp: Float, heightDp: Float, streakVisible: Boolean): TrackerLayout {
    val tier = tierForHeight(heightDp)
    val narrow = widthDp < NARROW_BELOW_WIDTH
    val pad = if (heightDp < COMPACT_PADDING_BELOW) 10f else 14f
    val showLetters = tier != WidgetTier.Strip || heightDp >= LETTERS_MIN_HEIGHT

    // gridWidth: what the 7 columns share once padding, the legend column and (strip) the leading
    // streak are taken out. availHeight: what is left for the cells once letters/header are.
    // The legend/streak widths are the design's reserved estimates, not measured text.
    val gridWidth: Float
    val availHeight: Float
    val cap: Float
    when (tier) {
        WidgetTier.Strip -> {
            gridWidth = widthDp - 2 * pad - (if (narrow) 60f else 78f) - 12f - (if (streakVisible) STRIP_STREAK_WIDTH_DP + 12f else 0f)
            availHeight = heightDp - 2 * pad - (if (showLetters) STRIP_LETTERS_RESERVE else 0f) - RING_ALLOWANCE
            cap = 34f
        }
        WidgetTier.Regular -> {
            gridWidth = widthDp - 2 * pad - (if (narrow) 70f else 86f) - 12f
            availHeight = heightDp - 2 * pad - 21f - RING_ALLOWANCE
            cap = 40f
        }
        WidgetTier.Tall -> {
            gridWidth = widthDp - 2 * pad
            availHeight = heightDp - 2 * pad - TALL_HEADER_HEIGHT_DP - 12f - 21f - RING_ALLOWANCE
            cap = 48f
        }
    }
    val column = gridWidth / 7f
    val diameter = floor(
        maxOf(MIN_CELL_DIAMETER, minOf(column * 0.8f, column - RING_ALLOWANCE, availHeight, cap)),
    ).toInt()

    val legendSize = when {
        tier == WidgetTier.Strip && narrow -> 11
        tier == WidgetTier.Strip || narrow -> 12
        else -> 13
    }
    val legendHeight = 2 * lineHeight(legendSize.toFloat()) + LEGEND_ROW_GAP_DP
    val placement = when {
        !streakVisible -> StreakPlacement.None
        tier == WidgetTier.Strip -> StreakPlacement.LeadingRow
        tier == WidgetTier.Regular -> StreakPlacement.BesideGrid
        else -> StreakPlacement.Header
    }

    return TrackerLayout(
        tier = tier,
        showLetters = showLetters,
        padding = pad,
        cellDiameter = diameter,
        letterSizeSp = (diameter * 0.34f).roundToInt().coerceIn(10, if (tier == WidgetTier.Strip) 11 else 13),
        legendSizeSp = legendSize,
        streakPlacement = placement,
        sideColumnHeight = when {
            tier == WidgetTier.Tall -> 0f
            placement == StreakPlacement.BesideGrid ->
                lineHeight(REGULAR_STREAK_SP) + SIDE_COLUMN_GAP_DP + legendHeight
            else -> legendHeight
        },
    )
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
