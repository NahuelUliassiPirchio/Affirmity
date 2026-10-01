package com.pirxhio.affirmity.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pirxhio.affirmity.data.DEFAULT_COLLECTION_HIGHLIGHT_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupHighlightTest {
    private fun argb(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL

    @Test
    fun `palette has the six design swatches with unique ids and distinct colours`() {
        assertEquals(6, GroupHighlight.entries.size)
        assertEquals(6, GroupHighlight.entries.map { it.id }.toSet().size)
        assertEquals(6, GroupHighlight.entries.map { it.color }.toSet().size)
    }

    @Test
    fun `every swatch is opaque`() {
        assertTrue(GroupHighlight.entries.all { it.color.alpha == 1f })
    }

    @Test
    fun `every swatch reads against the cover ground at 3 to 1 or better`() {
        GroupHighlight.entries.forEach { highlight ->
            val ratio = wcagContrastRatio(argb(highlight.color), argb(GroupCoverGround))
            assertTrue("${highlight.id} contrast $ratio", ratio >= 3.0)
        }
    }

    @Test
    fun `fromId resolves every swatch by its own id`() {
        GroupHighlight.entries.forEach { assertSame(it, GroupHighlight.fromId(it.id)) }
    }

    @Test
    fun `fromId falls back to the default for unknown or null ids`() {
        assertSame(GroupHighlight.Default, GroupHighlight.fromId("mauve"))
        assertSame(GroupHighlight.Default, GroupHighlight.fromId(null))
    }

    @Test
    fun `default highlight id matches the persisted column default`() {
        assertEquals(DEFAULT_COLLECTION_HIGHLIGHT_ID, GroupHighlight.Default.id)
    }
}
