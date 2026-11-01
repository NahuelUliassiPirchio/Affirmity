package com.pirxhio.affirmity.widget

import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.analytics.DailyGoal
import com.pirxhio.affirmity.data.DayRiskState
import com.pirxhio.affirmity.ui.theme.wcagContrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetBandTest {

    @Test
    fun `no band for done, in progress and not started`() {
        listOf(
            DayRiskState.Done,
            DayRiskState.InProgress(setOf(DailyGoal.MEDITATION)),
            DayRiskState.NotStarted,
        ).forEach {
            val band = widgetBandFor(it, "soft")
            assertFalse(band.visible)
            assertEquals(null, band.messageRes)
        }
    }

    @Test
    fun `goal incomplete is the soft band with the missing goal message`() {
        val band = widgetBandFor(DayRiskState.GoalIncomplete(DailyGoal.AFFIRMATION), "direct")
        assertEquals(BandStrength.Soft, band.strength)
        assertEquals(R.string.widget_band_direct_affirmation, band.messageRes)
    }

    @Test
    fun `streak at risk is the warm band`() {
        val band = widgetBandFor(DayRiskState.StreakAtRisk, "powerful")
        assertEquals(BandStrength.Warm, band.strength)
        assertEquals(R.string.widget_band_powerful_risk, band.messageRes)
    }

    @Test
    fun `null or unknown tone falls back to soft`() {
        val missing = DayRiskState.GoalIncomplete(DailyGoal.MEDITATION)
        assertEquals(R.string.widget_band_soft_meditation, widgetBandFor(missing, null).messageRes)
        assertEquals(R.string.widget_band_soft_meditation, widgetBandFor(missing, "other").messageRes)
        assertEquals(R.string.widget_band_manifestation_risk, widgetBandFor(DayRiskState.StreakAtRisk, "manifestation").messageRes)
    }

    @Test
    fun `text on each band meets WCAG AA`() {
        listOf(WidgetBandPalette.SOFT_BACKGROUND, WidgetBandPalette.WARM_BACKGROUND).forEach { bg ->
            val message = wcagContrastRatio(WidgetBandPalette.EMBER_ACCENT, bg)
            val number = wcagContrastRatio(WidgetBandPalette.NUMBER_ON_BAND, bg)
            println("band ${java.lang.Long.toHexString(bg)} message=%.2f number=%.2f".format(message, number))
            assertTrue("message $message", message >= 4.5)
            assertTrue("number $number", number >= 4.5)
        }
    }
}
