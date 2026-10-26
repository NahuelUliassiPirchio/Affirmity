package com.pirxhio.affirmity.ui.meditation

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatElapsedTest {

    @Test
    fun `formats elapsed millis as m colon ss`() {
        assertEquals("0:00", formatElapsed(0L))
        assertEquals("0:07", formatElapsed(7_400L))
        assertEquals("1:30", formatElapsed(90_000L))
        assertEquals("4:05", formatElapsed(245_000L))
    }

    @Test
    fun `a negative value is treated as zero`() {
        assertEquals("0:00", formatElapsed(-5L))
    }

    @Test
    fun `remaining time shows the whole second being counted down`() {
        assertEquals("1s", formatRemainingSeconds(999L))
        assertEquals("30s", formatRemainingSeconds(29_001L))
        assertEquals("30s", formatRemainingSeconds(29_999L))
    }

    @Test
    fun `remaining time moves to the next second exactly on the boundary`() {
        assertEquals("1s", formatRemainingSeconds(0L))
        assertEquals("2s", formatRemainingSeconds(1_000L))
        assertEquals("31s", formatRemainingSeconds(30_000L))
    }

    @Test
    fun `a negative remaining time is treated as zero`() {
        assertEquals("1s", formatRemainingSeconds(-5L))
    }
}
