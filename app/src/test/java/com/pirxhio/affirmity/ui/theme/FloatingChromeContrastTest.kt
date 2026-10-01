package com.pirxhio.affirmity.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Save-to glyph is primary on the floating chrome container (surface at 0.9 alpha). Checks the
 * glyph reads at 3:1 (WCAG non-text minimum) in both app colour schemes over the extreme
 * backgrounds the feed can put behind the pill. Dynamic colour is not covered.
 */
class FloatingChromeContrastTest {
    private val chromeAlpha = 0.9

    private fun channel(argb: Long, shift: Int) = ((argb shr shift) and 0xFF).toInt()

    /** Composites [surface] at [chromeAlpha] over an opaque [background]. */
    private fun pillOver(surface: Long, background: Long): Long {
        fun mix(shift: Int): Long =
            Math.round(channel(surface, shift) * chromeAlpha + channel(background, shift) * (1 - chromeAlpha))
        return 0xFF000000L or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun ratio(primary: Long, surface: Long, background: Long) =
        wcagContrastRatio(primary, pillOver(surface, background))

    @Test
    fun `dark scheme primary reads on the pill over black and white backgrounds`() {
        val primary = 0xFF5BBCC3L
        listOf(0xFF000000L, 0xFFFFFFFFL).forEach { background ->
            val r = ratio(primary, 0xFF000000L, background)
            assertTrue("dark over ${background.toString(16)} = $r", r >= 3.0)
        }
    }

    @Test
    fun `light scheme primary reads on the pill over black and white backgrounds`() {
        val primary = 0xFF00696FL
        listOf(0xFF000000L, 0xFFFFFFFFL).forEach { background ->
            val r = ratio(primary, 0xFFF9F9F9L, background)
            assertTrue("light over ${background.toString(16)} = $r", r >= 3.0)
        }
    }
}
