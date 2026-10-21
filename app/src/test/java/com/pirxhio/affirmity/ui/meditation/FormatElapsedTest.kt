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
}
