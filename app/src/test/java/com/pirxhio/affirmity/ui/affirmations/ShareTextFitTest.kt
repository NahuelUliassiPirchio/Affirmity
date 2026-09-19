package com.pirxhio.affirmity.ui.affirmations

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTextFitTest {
    @Test
    fun picksLargestSizeThatFits() {
        assertEquals(60f, fitTextSize(listOf(80f, 60f, 40f)) { it <= 60f }, 0f)
    }

    @Test
    fun keepsLargestWhenEverythingFits() {
        assertEquals(80f, fitTextSize(listOf(80f, 60f, 40f)) { true }, 0f)
    }

    @Test
    fun fallsBackToSmallestWhenNothingFits() {
        assertEquals(40f, fitTextSize(listOf(80f, 60f, 40f)) { false }, 0f)
    }

    @Test
    fun sizeCandidatesAreStrictlyDescending() {
        val c = shareTextSizeCandidates(largest = 96f, smallest = 40f, step = 4f)
        assertEquals(96f, c.first(), 0f)
        assertEquals(40f, c.last(), 0f)
        assertEquals(c.sortedDescending(), c)
    }
}
