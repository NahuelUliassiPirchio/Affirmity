package com.pirxhio.affirmity.ui.milestone

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreakMilestoneScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun show(days: Int, onConfirm: () -> Unit = {}): Context {
        val configuration = Configuration(composeTestRule.activity.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val context = composeTestRule.activity.createConfigurationContext(configuration)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
                MaterialTheme { StreakMilestoneScreen(milestoneDays = days, onConfirm = onConfirm) }
            }
        }
        return context
    }

    @Test
    fun sevenDaysShowsTitleSubtitleAndNextTarget() {
        val context = show(7)
        composeTestRule.onNodeWithText(context.getString(R.string.streak_milestone_title, 7)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.streak_milestone_subtitle_7)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.streak_milestone_explanation_next, 14))
            .assertIsDisplayed()
    }

    @Test
    fun finalMilestoneShowsFinalExplanation() {
        val context = show(365)
        composeTestRule.onNodeWithText(context.getString(R.string.streak_milestone_explanation_final))
            .assertIsDisplayed()
    }

    @Test
    fun confirmInvokesCallback() {
        var confirmed = 0
        val context = show(7) { confirmed++ }
        composeTestRule.onNodeWithText(context.getString(R.string.streak_milestone_confirm_button)).performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun contentIsScrollable() {
        show(7)
        composeTestRule.onNode(hasScrollAction()).assertExists()
    }
}
