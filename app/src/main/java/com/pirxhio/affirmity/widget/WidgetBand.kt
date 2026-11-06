package com.pirxhio.affirmity.widget

import androidx.annotation.StringRes
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.analytics.DailyGoal
import com.pirxhio.affirmity.data.DayRiskState

/**
 * Colors of the end-of-day band (DESIGN.md ember family): the whole weekly widget and the in-app
 * dialog take these grounds. Packed 0xAARRGGBB longs so the contrast test can check them without
 * Compose. The `widget_card_bg_soft` / `widget_card_bg_warm` drawables repeat the two backgrounds
 * as XML (Glance needs a drawable for rounded corners); keep them in sync.
 */
internal object WidgetBandPalette {
    /** streak-ground-night: the softer band (goal incomplete, streak safe). */
    const val SOFT_BACKGROUND = 0xFF3A2412L
    /** A stronger ember tint for the streak-at-risk band. */
    const val WARM_BACKGROUND = 0xFF55290CL
    /** streak-ember-night: accent text and lit flame on both bands. */
    const val EMBER_ACCENT = 0xFFFF9A4DL
    /** tracker-text: the streak number and today's letter stay neutral on the band. */
    const val NUMBER_ON_BAND = 0xFFE9E9EDL
}

internal enum class BandStrength { None, Soft, Warm }

internal data class WidgetBand(
    val strength: BandStrength,
    val backgroundColor: Long?,
    val messageColor: Long?,
    val flameColor: Long?,
    @StringRes val messageRes: Int?,
) {
    val visible: Boolean get() = strength != BandStrength.None
}

private val NO_BAND = WidgetBand(BandStrength.None, null, null, null, null)

/** Maps the day's risk state and the onboarding tone to the band; unknown or null tone is soft. */
internal fun widgetBandFor(state: DayRiskState, tone: String?): WidgetBand = when (state) {
    is DayRiskState.GoalIncomplete -> WidgetBand(
        strength = BandStrength.Soft,
        backgroundColor = WidgetBandPalette.SOFT_BACKGROUND,
        messageColor = WidgetBandPalette.EMBER_ACCENT,
        flameColor = WidgetBandPalette.EMBER_ACCENT,
        messageRes = goalIncompleteMessage(tone, state.missing),
    )
    DayRiskState.StreakAtRisk -> WidgetBand(
        strength = BandStrength.Warm,
        backgroundColor = WidgetBandPalette.WARM_BACKGROUND,
        messageColor = WidgetBandPalette.EMBER_ACCENT,
        flameColor = WidgetBandPalette.EMBER_ACCENT,
        messageRes = atRiskMessage(tone),
    )
    else -> NO_BAND
}

private fun goalIncompleteMessage(tone: String?, missing: DailyGoal): Int {
    val meditation = missing == DailyGoal.MEDITATION
    return when (tone) {
        "direct" -> if (meditation) R.string.widget_band_direct_meditation else R.string.widget_band_direct_affirmation
        "powerful" -> if (meditation) R.string.widget_band_powerful_meditation else R.string.widget_band_powerful_affirmation
        "manifestation" -> if (meditation) R.string.widget_band_manifestation_meditation else R.string.widget_band_manifestation_affirmation
        else -> if (meditation) R.string.widget_band_soft_meditation else R.string.widget_band_soft_affirmation
    }
}

private fun atRiskMessage(tone: String?): Int = when (tone) {
    "direct" -> R.string.widget_band_direct_risk
    "powerful" -> R.string.widget_band_powerful_risk
    "manifestation" -> R.string.widget_band_manifestation_risk
    else -> R.string.widget_band_soft_risk
}
