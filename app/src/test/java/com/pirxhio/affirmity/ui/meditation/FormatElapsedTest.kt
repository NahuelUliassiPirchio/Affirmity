package com.pirxhio.affirmity.ui.meditation

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatElapsedTest {

    @Test
    fun `formats elapsed millis as mm colon ss`() {
        assertEquals("00:00", formatElapsed(0L))
        assertEquals("00:07", formatElapsed(7_400L))
        assertEquals("01:30", formatElapsed(90_000L))
        assertEquals("04:05", formatElapsed(245_000L))
    }

    @Test
    fun `over an hour keeps counting total minutes`() {
        assertEquals("75:00", formatElapsed(4_500_000L))
    }

    @Test
    fun `a negative value is treated as zero`() {
        assertEquals("00:00", formatElapsed(-5L))
    }

    @Test
    fun `remaining time shows the whole second being counted down`() {
        assertEquals("00:01", formatRemainingSeconds(999L))
        assertEquals("00:30", formatRemainingSeconds(29_001L))
        assertEquals("00:30", formatRemainingSeconds(29_999L))
    }

    @Test
    fun `remaining time moves to the next second exactly on the boundary`() {
        assertEquals("00:01", formatRemainingSeconds(0L))
        assertEquals("00:02", formatRemainingSeconds(1_000L))
        assertEquals("00:31", formatRemainingSeconds(30_000L))
    }

    @Test
    fun `remaining time rolls past a minute as mm colon ss`() {
        assertEquals("01:30", formatRemainingSeconds(89_500L))
    }

    @Test
    fun `a negative remaining time is treated as zero`() {
        assertEquals("00:01", formatRemainingSeconds(-5L))
    }
}
