package com.pirxhio.affirmity.widget.carousel

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselLayoutTest {

    @Test
    fun `size follows the responsive breakpoints`() {
        assertEquals(CarouselSize.Small, carouselSizeFor(110.dp, 110.dp))
        assertEquals(CarouselSize.Small, carouselSizeFor(0.dp, 0.dp))
        assertEquals(CarouselSize.Medium, carouselSizeFor(250.dp, 110.dp))
        assertEquals(CarouselSize.Medium, carouselSizeFor(300.dp, 200.dp))
        assertEquals(CarouselSize.Large, carouselSizeFor(250.dp, 250.dp))
        assertEquals(CarouselSize.Large, carouselSizeFor(400.dp, 400.dp))
        // Tall but narrow stays small: the card cannot hold the large layout.
        assertEquals(CarouselSize.Small, carouselSizeFor(110.dp, 300.dp))
    }

    @Test
    fun `layout specs match design 8`() {
        val small = carouselLayoutSpec(CarouselSize.Small)
        val medium = carouselLayoutSpec(CarouselSize.Medium)
        val large = carouselLayoutSpec(CarouselSize.Large)

        assertEquals(listOf(19f, 25f, 33f), listOf(small, medium, large).map { it.titleSp })
        assertEquals(listOf(11.5f, 13.5f, 16f), listOf(small, medium, large).map { it.subtitleSp })
        assertEquals(2, small.subtitleMaxLines)
        assertEquals(14.dp, small.paddingHorizontal)
        assertEquals(16.dp, medium.paddingHorizontal)
        assertEquals(15.dp, medium.paddingVertical)
        assertEquals(18.dp, large.paddingHorizontal)
        assertTrue(large.showDivider)
        assertFalse(small.showDivider || medium.showDivider)
        assertEquals(listOf(0.24f, 0.22f, 0.18f), listOf(small, medium, large).map { it.markerAlpha })
    }

    @Test
    fun `small shows only the star`() {
        assertNull(headerLabel(CarouselSize.Small, "Self-worth"))
    }

    @Test
    fun `medium shows the uppercase theme name`() {
        assertEquals("SELF-WORTH", headerLabel(CarouselSize.Medium, "Self-worth"))
        assertNull(headerLabel(CarouselSize.Medium, null))
        assertNull(headerLabel(CarouselSize.Medium, "  "))
    }

    @Test
    fun `large shows the brand then the theme`() {
        assertEquals("AFFIRMITY · SELF-WORTH", headerLabel(CarouselSize.Large, "Self-worth"))
        assertEquals("AFFIRMITY", headerLabel(CarouselSize.Large, null))
    }
}
