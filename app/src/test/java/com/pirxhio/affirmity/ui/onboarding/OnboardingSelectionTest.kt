package com.pirxhio.affirmity.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingSelectionTest {
    @Test
    fun `single-select replaces while multi-select toggles and respects its cap`() {
        assertEquals(setOf("direct"), toggleSelection(setOf("soft"), "direct", 1))
        assertEquals(setOf("calm", "confidence"), toggleSelection(setOf("calm"), "confidence", 3))
        assertEquals(setOf("calm"), toggleSelection(setOf("calm", "confidence"), "confidence", 3))
        assertEquals(setOf("a", "b", "c"), toggleSelection(setOf("a", "b", "c"), "d", 3))
    }
}
