package com.pirxhio.affirmity.ui.progress

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarGridLayoutTest {
    @Test fun sevenTouchTargetsFitWithoutGaps() {
        assertTrue(CalendarGridLayout.fits(336.dp))
        assertFalse(CalendarGridLayout.fits(335.dp))
    }

    @Test fun typicalPhoneContentWidthFitsWithoutScrolling() {
        // 411dp phone minus 24dp page padding on each side.
        assertTrue(CalendarGridLayout.fits(363.dp))
    }

    @Test fun threeSixtyDpPhoneFitsOnlyByBleedingIntoThePagePadding() {
        // 360dp phone minus 24dp page padding on each side.
        assertFalse(CalendarGridLayout.fits(312.dp))
        assertTrue(CalendarGridLayout.fits(312.dp, bleed = 16.dp))
    }

    @Test fun veryNarrowWidthFallsBackToScrolling() {
        assertFalse(CalendarGridLayout.fits(272.dp))
        assertFalse(CalendarGridLayout.fits(272.dp, bleed = 16.dp))
    }

    @Test fun largeFontScaleSwitchesToNarrowWeekdays() {
        assertFalse(CalendarGridLayout.usesNarrowWeekdays(1.0f))
        assertTrue(CalendarGridLayout.usesNarrowWeekdays(2.0f))
    }

    @Test fun weekdayLabelsFollowTheLocaleWeekStartAndStaySingleCharacter() {
        val us = weekdayLabels(java.util.Locale.US, narrow = false)
        assertEquals("Sun", us.first().short)
        assertEquals("Sunday", us.first().full)
        assertEquals(7, us.size)
        val es = weekdayLabels(java.util.Locale("es", "ES"), narrow = false)
        assertEquals("lunes", es.first().full)
        assertEquals("domingo", es.last().full)
        // Narrow ("EEEEE") single-letter output is Android/ICU behaviour; the JDK formatter returns full names, so it is
        // covered on-device by the font-scale 2.0 screenshot and weekdayHeadersStaySingleLineAtLargeFontScale.
    }
}
