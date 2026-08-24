package com.pirxhio.affirmity.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.auth.AuthState
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.QuietHoursSettings
import com.pirxhio.affirmity.ui.onboarding.guide.OnboardingGuideScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers task 5.5 (spec R5.1-R5.4): Settings' "How Affirmity works" row opens the guide at slide
 * 1, and closing it commits guide-seen via the SAME `onDismiss` callback the auto-show gate uses
 * -- it never re-arms the DataStore auto flag (there is no separate "re-arm" API surface for the
 * manual path: [com.pirxhio.affirmity.data.AffirmityAppState.markOnboardingGuideSeen] only ever
 * writes `true`). Requires a connected device/emulator (`connectedDebugAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class SettingsOnboardingGuideRowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingTheRowInvokesOnOpenOnboardingGuide() {
        var opened = false
        composeTestRule.setContent {
            SettingsScreen(
                reminderSettings = ChannelSettings(enabled = false, segments = emptySet()),
                reflectionSettings = ChannelSettings(enabled = false, segments = emptySet()),
                moodSettings = ChannelSettings(enabled = false, segments = emptySet()),
                quietHoursSettings = QuietHoursSettings(enabled = false, startMinute = 1380, endMinute = 420),
                notificationsPermissionGranted = true,
                authState = AuthState.SignedOut,
                onReminderEnabledChanged = {},
                onReminderSegmentsChanged = {},
                onReflectionEnabledChanged = {},
                onReflectionSegmentsChanged = {},
                onMoodEnabledChanged = {},
                onMoodSegmentsChanged = {},
                onQuietHoursEnabledChanged = {},
                onQuietHoursWindowChanged = { _, _ -> },
                onOpenNotificationDebug = {},
                onOpenOnboardingGuide = { opened = true },
                onSignInClicked = {},
                onSignOutClicked = {},
                tier = AccessTier.FREE,
                onUpgradeClick = {},
                onManageSubscriptionClick = {},
            )
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.settings_onboarding_guide_open_button),
        ).performClick()

        assertTrue("tapping the row must invoke onOpenOnboardingGuide", opened)
    }

    @Test
    fun manualReEntryOpensTheGuideStartingAtSlide1AndOnDismissCommitsSeenExactlyOnce() {
        var seenCommits = 0
        var manuallyOpen = true
        composeTestRule.setContent {
            if (manuallyOpen) {
                // R5.2: manual open always starts at slide 1 -- OnboardingGuideScreen has no
                // "resume at slide N" parameter, so a fresh composition always begins there.
                OnboardingGuideScreen(
                    onDismiss = {
                        seenCommits++
                        manuallyOpen = false
                    },
                )
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_guide_slide1_title),
        ).assertExists()

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_guide_skip_button),
        ).performClick()

        assertEquals("closing the manually-opened guide must commit seen exactly once", 1, seenCommits)
    }
}
