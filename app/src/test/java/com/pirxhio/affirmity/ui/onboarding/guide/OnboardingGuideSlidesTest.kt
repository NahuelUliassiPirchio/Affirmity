package com.pirxhio.affirmity.ui.onboarding.guide

import com.pirxhio.affirmity.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers task 3.1 (spec R3.1-R3.4): [onboardingGuideSlides] returns exactly 4 slides, in fixed
 * order, with unique title/body resources, and slide 4's copy frames the sanador as a repair
 * mechanic on the SAME general streak -- not a second counter (spec R3.4 hard requirement, ties to
 * proposal Risk #1). Mirrors [com.pirxhio.affirmity.ui.meditation.catalog.MeditationCatalogTest]'s
 * style.
 */
class OnboardingGuideSlidesTest {

    @Test
    fun `onboardingGuideSlides returns exactly 4 slides`() {
        val slides = onboardingGuideSlides()

        assertEquals(4, slides.size)
    }

    @Test
    fun `every slide has a unique titleRes and bodyRes`() {
        val slides = onboardingGuideSlides()

        val titleIds = slides.map { it.titleRes }
        val bodyIds = slides.map { it.bodyRes }
        assertEquals("titleRes must be unique per slide", titleIds.size, titleIds.toSet().size)
        assertEquals("bodyRes must be unique per slide", bodyIds.size, bodyIds.toSet().size)
    }

    @Test
    fun `slide 1 is affirmations, slide 2 is meditations, slide 3 is mood, slide 4 is streak`() {
        val slides = onboardingGuideSlides()

        assertEquals(R.string.onboarding_guide_slide1_title, slides[0].titleRes)
        assertEquals(R.string.onboarding_guide_slide2_title, slides[1].titleRes)
        assertEquals(R.string.onboarding_guide_slide3_title, slides[2].titleRes)
        assertEquals(R.string.onboarding_guide_slide4_title, slides[3].titleRes)
    }

    @Test
    fun `slide 4 body resolves to the single general-streak copy resource`() {
        val slides = onboardingGuideSlides()

        // Spec R3.4 hard requirement: there is exactly ONE streak-related body string used by
        // slide 4 (onboarding_guide_slide4_body). Its actual copy is verified against R3.4's exact
        // wording constraints by human review of strings.xml (a single-streak-language gate, not a
        // machine-checkable string match) -- this test locks the *identity* of the resource so a
        // future edit can't silently swap in a different/second-streak string without this test
        // catching the resource-id drift.
        assertEquals(R.string.onboarding_guide_slide4_body, slides[3].bodyRes)
    }

    @Test
    fun `every slide has an icon content description resource`() {
        val slides = onboardingGuideSlides()

        slides.forEach { slide ->
            assertTrue(slide.iconContentDescriptionRes != 0)
        }
    }
}
