package com.pirxhio.affirmity.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPlayAccentTest {
    private fun argb(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL

    @Test
    fun `play glyph reads on the accent circle at 4_5 to 1 or better`() {
        val ratio = wcagContrastRatio(argb(GroupPlayOnAccent), argb(GroupPlayAccent))
        assertTrue("glyph contrast $ratio", ratio >= 4.5)
    }

    @Test
    fun `accent circle stands out from the cover-ground ring that separates it from every highlight`() {
        assertTrue(wcagContrastRatio(argb(GroupPlayAccent), argb(GroupCoverGround)) >= 3.0)
    }
}
