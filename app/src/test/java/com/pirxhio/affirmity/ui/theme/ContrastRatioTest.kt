package com.pirxhio.affirmity.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug 2b: the selected-nav-tab indicator pill (secondaryContainer) was nearly indistinguishable
 * from the nav bar background (surfaceContainer) -- 0xFFE2E2E2 vs 0xFFEEEEEE. [wcagContrastRatio]
 * is the pure WCAG relative-luminance formula extracted so the chosen replacement colors are
 * JVM-testable without rendering Compose (pattern: `resolveSelectedGroupIds` in
 * `data/AffirmityAppState.kt`).
 */
class ContrastRatioTest {

    @Test
    fun `black on white is the maximum 21 to 1 ratio`() {
        assertEquals(21.0, wcagContrastRatio(0xFF000000, 0xFFFFFFFF), 0.01)
    }

    @Test
    fun `identical colors have a 1 to 1 ratio`() {
        assertEquals(1.0, wcagContrastRatio(0xFF5BBCC3, 0xFF5BBCC3), 0.001)
    }

    @Test
    fun `ratio is symmetric regardless of argument order`() {
        val ab = wcagContrastRatio(0xFF00696F, 0xFFEEEEEE)
        val ba = wcagContrastRatio(0xFFEEEEEE, 0xFF00696F)
        assertEquals(ab, ba, 0.0001)
    }

    @Test
    fun `the old secondaryContainer barely stands out from surfaceContainer, under the 3 to 1 UI-component minimum`() {
        val oldSecondaryContainerLight = 0xFFE2E2E2
        val ratio = wcagContrastRatio(oldSecondaryContainerLight, SurfaceContainerLightArgb)
        assertTrue("expected the old pairing to fail 3:1, was $ratio", ratio < 3.0)
    }

    @Test
    fun `the fixed light secondaryContainer clears the 3 to 1 UI-component minimum against surfaceContainer`() {
        val ratio = wcagContrastRatio(SecondaryContainerLightArgb, SurfaceContainerLightArgb)
        assertTrue("expected >= 3.0, was $ratio", ratio >= 3.0)
    }

    @Test
    fun `the fixed light onSecondaryContainer clears the 4-5 to 1 text minimum against secondaryContainer`() {
        val ratio = wcagContrastRatio(OnSecondaryContainerLightArgb, SecondaryContainerLightArgb)
        assertTrue("expected >= 4.5, was $ratio", ratio >= 4.5)
    }

    @Test
    fun `the dark secondaryContainer clears the 3 to 1 UI-component minimum against surfaceContainer`() {
        val ratio = wcagContrastRatio(SecondaryContainerDarkArgb, SurfaceContainerDarkArgb)
        assertTrue("expected >= 3.0, was $ratio", ratio >= 3.0)
    }

    @Test
    fun `the dark onSecondaryContainer clears the 4-5 to 1 text minimum against secondaryContainer`() {
        val ratio = wcagContrastRatio(OnSecondaryContainerDarkArgb, SecondaryContainerDarkArgb)
        assertTrue("expected >= 4.5, was $ratio", ratio >= 4.5)
    }

    @Test
    fun `the dark outline clears the 3 to 1 UI-component minimum against background`() {
        val ratio = wcagContrastRatio(OutlineDarkArgb, BackgroundDarkArgb)
        assertTrue("expected >= 3.0, was $ratio", ratio >= 3.0)
    }
}
