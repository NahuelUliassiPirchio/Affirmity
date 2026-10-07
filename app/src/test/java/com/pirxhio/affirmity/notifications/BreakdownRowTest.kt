package com.pirxhio.affirmity.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class BreakdownRowTest {

    // Pure stand-ins for the Android plural/format resources (Spanish and English shapes).
    private val esDays = { n: Int -> if (n == 1) "$n día" else "$n días" }
    private val enDays = { n: Int -> if (n == 1) "$n day" else "$n days" }
    private val line = { label: String, days: String -> "$label: $days" }

    private fun streak(med: Int?, aff: Int?) =
        NotificationStyleSpec.Streak("t", "b", count = 7, activity = null, meditationDays = med, affirmationsDays = aff)

    @Test
    fun `both live shows both rows with their own day counts`() {
        val spec = streak(med = 7, aff = 4)

        assertEquals(
            BreakdownRow.Visible("Meditación: 7 días"),
            breakdownRow(spec.meditationDays, "Meditación", esDays, line),
        )
        assertEquals(
            BreakdownRow.Visible("Afirmaciones: 4 días"),
            breakdownRow(spec.affirmationsDays, "Afirmaciones", esDays, line),
        )
    }

    @Test
    fun `meditation only hides the affirmations row`() {
        val spec = streak(med = 5, aff = null)

        assertEquals(
            BreakdownRow.Visible("Meditation: 5 days"),
            breakdownRow(spec.meditationDays, "Meditation", enDays, line),
        )
        assertEquals(BreakdownRow.Hidden, breakdownRow(spec.affirmationsDays, "Affirmations", enDays, line))
    }

    @Test
    fun `affirmations only hides the meditation row`() {
        val spec = streak(med = null, aff = 5)

        assertEquals(BreakdownRow.Hidden, breakdownRow(spec.meditationDays, "Meditación", esDays, line))
        assertEquals(
            BreakdownRow.Visible("Afirmaciones: 5 días"),
            breakdownRow(spec.affirmationsDays, "Afirmaciones", esDays, line),
        )
    }

    @Test
    fun `singular and plural are resolved per count`() {
        assertEquals(BreakdownRow.Visible("Meditation: 1 day"), breakdownRow(1, "Meditation", enDays, line))
        assertEquals(BreakdownRow.Visible("Meditación: 1 día"), breakdownRow(1, "Meditación", esDays, line))
        assertEquals(BreakdownRow.Visible("Meditation: 2 days"), breakdownRow(2, "Meditation", enDays, line))
    }

    @Test
    fun `zero or negative days hides the row instead of printing 0 days`() {
        assertEquals(BreakdownRow.Hidden, breakdownRow(0, "Meditation", enDays, line))
        assertEquals(BreakdownRow.Hidden, breakdownRow(-1, "Meditation", enDays, line))
    }
}
