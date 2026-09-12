package com.pirxhio.affirmity.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.auth.AuthState
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.QuietHoursSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsMyGoalsRowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingMyGoalsInvokesTheNavigationCallback() {
        var opened = false
        composeTestRule.setContent {
            SettingsScreen(
                reminderSettings = disabledChannel(),
                reflectionSettings = disabledChannel(),
                moodSettings = disabledChannel(),
                streakSettings = disabledChannel(),
                healerSettings = disabledChannel(),
                meditationReturnSettings = disabledChannel(),
                quietHoursSettings = QuietHoursSettings(enabled = false, startMinute = 1380, endMinute = 420),
                notificationsPermissionGranted = true,
                authState = AuthState.SignedOut,
                onReminderEnabledChanged = {},
                onReminderSegmentsChanged = {},
                onReflectionEnabledChanged = {},
                onReflectionSegmentsChanged = {},
                onMoodEnabledChanged = {},
                onMoodSegmentsChanged = {},
                onStreakEnabledChanged = {},
                onHealerEnabledChanged = {},
                onMeditationReturnEnabledChanged = {},
                onNotificationEnableRequested = {},
                onQuietHoursEnabledChanged = {},
                onQuietHoursWindowChanged = { _, _ -> },
                onOpenNotificationDebug = {},
                onOpenOnboardingGuide = {},
                onOpenMyGoals = { opened = true },
                onOpenHiddenAffirmations = {},
                onSignInClicked = {},
                onSignOutClicked = {},
                tier = AccessTier.FREE,
                onUpgradeClick = {},
                onManageSubscriptionClick = {},
            )
        }

        composeTestRule.onNodeWithText("Editar").performClick()

        assertTrue(opened)
    }
}

private fun disabledChannel() = ChannelSettings(enabled = false, segments = emptySet())
