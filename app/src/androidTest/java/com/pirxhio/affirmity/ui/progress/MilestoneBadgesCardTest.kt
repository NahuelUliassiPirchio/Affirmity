package com.pirxhio.affirmity.ui.progress

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.StreakMilestones
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MilestoneBadgesCardTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context

    private fun show(best: Int, streak: Int) {
        val configuration = Configuration(composeTestRule.activity.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        context = composeTestRule.activity.createConfigurationContext(configuration)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
                MaterialTheme { MilestoneBadgesCard(bestMilestoneReached = best, generalStreakDays = streak) }
            }
        }
    }

    private fun reached(days: Int) = context.getString(R.string.progress_milestone_reached_content_description, days)
    private fun locked(days: Int) = context.getString(R.string.progress_milestone_locked_content_description, days)
    private fun next(remaining: Int, target: Int) =
        context.resources.getQuantityString(R.plurals.progress_milestones_next, remaining, remaining, target)

    @Test
    fun badgesAreReachedOrLockedFromLifetimeBestAndNextLineUsesCurrentStreak() {
        show(best = 7, streak = 5)
        listOf(1, 3, 7).forEach {
            composeTestRule.onAllNodesWithContentDescription(reached(it)).assertCountEquals(1)
            composeTestRule.onAllNodesWithContentDescription(locked(it)).assertCountEquals(0)
        }
        listOf(14, 30, 60, 100, 180, 365).forEach {
            composeTestRule.onAllNodesWithContentDescription(locked(it)).assertCountEquals(1)
            composeTestRule.onAllNodesWithContentDescription(reached(it)).assertCountEquals(0)
        }
        composeTestRule.onNodeWithText(next(2, 7)).assertIsDisplayed()
    }

    @Test
    fun zeroStreakTargetsThreeNeverOne() {
        show(best = 0, streak = 0)
        composeTestRule.onNodeWithText(next(3, 3)).assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription(locked(1)).assertCountEquals(1)
    }

    @Test
    fun allReachedShowsFinalLine() {
        show(best = 365, streak = 365)
        StreakMilestones.ALL.forEach {
            composeTestRule.onAllNodesWithContentDescription(reached(it)).assertCountEquals(1)
        }
        composeTestRule.onNodeWithText(context.getString(R.string.progress_milestones_all_reached)).assertIsDisplayed()
    }
}
