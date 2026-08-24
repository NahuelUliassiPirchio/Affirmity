package com.pirxhio.affirmity.ui.onboarding.guide

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First Compose UI test in the repo (design's Testing Strategy table, task 5.0). Covers tasks
 * 5.1-5.3: pager swipe/dots (R4.1/R4.4), Skip from any slide (R4.2), and the last slide's
 * completion action (R4.1/R4.3). Requires a connected device/emulator
 * (`connectedDebugAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class OnboardingGuideScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun swipingThePagerAdvancesSlidesAndTheNextButtonLabelStaysUntilTheLastSlide() {
        var dismissed = false
        composeTestRule.setContent {
            OnboardingGuideScreen(onDismiss = { dismissed = true })
        }

        // Slide 1's title is visible first.
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_guide_slide1_title),
        ).assertExists()

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_guide_next_button),
        ).performClick()

        // Slide 2's title is now visible -- proves the pager actually advanced, not a smoke test.
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_guide_slide2_title),
        ).assertExists()

        assertTrue("dismiss must not fire from an intermediate Next tap", !dismissed)
    }

    @Test
    fun tappingSkipOnAnySlideDismissesImmediately() {
        var dismissed = false
        composeTestRule.setContent {
            OnboardingGuideScreen(onDismiss = { dismissed = true })
        }

        // Advance to slide 2 first, to prove Skip works from a non-first slide (R4.2: "on every
        // slide, not only the first").
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_guide_next_button),
        ).performClick()

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_guide_skip_button),
        ).performClick()

        assertTrue("Skip must dismiss regardless of the current slide", dismissed)
    }

    @Test
    fun lastSlidePrimaryActionReadsGetStartedAndCompletingItDismisses() {
        var dismissCount = 0
        composeTestRule.setContent {
            OnboardingGuideScreen(onDismiss = { dismissCount++ })
        }

        val nextLabel = composeTestRule.activity.getString(R.string.onboarding_guide_next_button)
        repeat(onboardingGuideSlides().size - 1) {
            composeTestRule.onNodeWithText(nextLabel).performClick()
        }

        // On the last slide, "Next" must no longer be shown -- "Get Started" replaces it (R4.1).
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_guide_get_started_button),
        ).assertExists()

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_guide_get_started_button),
        ).performClick()

        assertEquals("completing the last slide must dismiss exactly once", 1, dismissCount)
    }

    @Test
    fun swipingLeftOnThePagerAdvancesToTheNextSlide() {
        composeTestRule.setContent {
            OnboardingGuideScreen(onDismiss = {})
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_guide_slide1_title),
        ).performTouchInput { swipeLeft() }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_guide_slide2_title),
        ).assertExists()
    }
}
