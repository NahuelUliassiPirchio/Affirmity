package com.pirxhio.affirmity.ui.meditation

import androidx.compose.ui.graphics.toArgb
import com.pirxhio.affirmity.ui.theme.BackgroundDarkArgb
import com.pirxhio.affirmity.ui.theme.BackgroundLight
import com.pirxhio.affirmity.ui.theme.OnSurfaceDark
import com.pirxhio.affirmity.ui.theme.OnSurfaceLight
import com.pirxhio.affirmity.ui.theme.OnSurfaceVariantDark
import com.pirxhio.affirmity.ui.theme.OnSurfaceVariantLight
import com.pirxhio.affirmity.ui.theme.wcagContrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

class AuroraContrastTest {

    private val backgroundLight = BackgroundLight.toArgb().toLong()
    private val onSurfaceLight = OnSurfaceLight.toArgb().toLong()
    private val onSurfaceVariantLight = OnSurfaceVariantLight.toArgb().toLong()
    private val onSurfaceDark = OnSurfaceDark.toArgb().toLong()
    private val onSurfaceVariantDark = OnSurfaceVariantDark.toArgb().toLong()

    private fun over(fg: Long, alpha: Float, bg: Long): Long {
        fun ch(shift: Int): Long {
            val f = ((fg shr shift) and 0xFF).toFloat()
            val b = ((bg shr shift) and 0xFF).toFloat()
            return Math.round(f * alpha + b * (1f - alpha)).toLong()
        }
        return 0xFF000000 or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Every field fully overlapping at peak alpha is the worst a pixel can ever get. */
    private fun worstCases(dark: Boolean, background: Long): List<Long> {
        val fields = auroraPalette(dark).fields
        val stacked = fields.fold(background) { acc, f -> over(f.argb, f.alpha, acc) }
        // The warm state swaps the sea field for blush; stack that too.
        val blush = fields.last()
        val warm = listOf(fields[0], blush, blush).fold(background) { acc, f -> over(f.argb, f.alpha, acc) }
        return fields.map { over(it.argb, it.alpha, background) } + stacked + warm
    }

    @Test
    fun `light theme text keeps AA over the worst stacked aurora`() {
        worstCases(false, backgroundLight).forEach { bg ->
            assertTrue(wcagContrastRatio(onSurfaceLight, bg) >= 4.5)
            assertTrue(wcagContrastRatio(onSurfaceVariantLight, bg) >= 4.5)
        }
    }

    @Test
    fun `dark theme text keeps AA over the worst stacked aurora`() {
        worstCases(true, BackgroundDarkArgb).forEach { bg ->
            assertTrue(wcagContrastRatio(onSurfaceDark, bg) >= 4.5)
            assertTrue(wcagContrastRatio(onSurfaceVariantDark, bg) >= 4.5)
        }
    }
}
