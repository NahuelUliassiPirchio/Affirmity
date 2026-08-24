package com.pirxhio.affirmity.ui.onboarding.guide

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.pirxhio.affirmity.R

/**
 * Content-agnostic slide data (design D6) -- keeps [onboardingGuideSlides] a pure, JVM-testable
 * function. [OnboardingGuideScreen] renders these via [OnboardingGuideSlideContent], mirroring
 * [com.pirxhio.affirmity.ui.healer.StreakHealerGrantedScreen]'s icon-in-circle/headline/body
 * template.
 */
data class OnboardingGuideSlide(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @StringRes val iconContentDescriptionRes: Int,
)

/** Exactly 4 slides, in fixed order (spec R3): affirmations, meditations, mood, streak+healer. */
fun onboardingGuideSlides(): List<OnboardingGuideSlide> = listOf(
    OnboardingGuideSlide(
        icon = Icons.Filled.AutoAwesome,
        titleRes = R.string.onboarding_guide_slide1_title,
        bodyRes = R.string.onboarding_guide_slide1_body,
        iconContentDescriptionRes = R.string.onboarding_guide_slide1_icon_content_description,
    ),
    OnboardingGuideSlide(
        icon = Icons.Filled.Timer,
        titleRes = R.string.onboarding_guide_slide2_title,
        bodyRes = R.string.onboarding_guide_slide2_body,
        iconContentDescriptionRes = R.string.onboarding_guide_slide2_icon_content_description,
    ),
    OnboardingGuideSlide(
        icon = Icons.Filled.Mood,
        titleRes = R.string.onboarding_guide_slide3_title,
        bodyRes = R.string.onboarding_guide_slide3_body,
        iconContentDescriptionRes = R.string.onboarding_guide_slide3_icon_content_description,
    ),
    // R3.4 hard requirement: this slide's body copy (onboarding_guide_slide4_body) frames the
    // sanador as a repair on the SAME general streak, never a second/parallel counter.
    OnboardingGuideSlide(
        icon = Icons.Filled.Favorite,
        titleRes = R.string.onboarding_guide_slide4_title,
        bodyRes = R.string.onboarding_guide_slide4_body,
        iconContentDescriptionRes = R.string.onboarding_guide_slide4_icon_content_description,
    ),
)
