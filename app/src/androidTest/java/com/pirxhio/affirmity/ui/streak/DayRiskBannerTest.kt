package com.pirxhio.affirmity.ui.streak

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.analytics.DailyGoal
import com.pirxhio.affirmity.data.DayRiskState
import com.pirxhio.affirmity.widget.widgetBandFor
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayRiskBannerTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsMessageAndA11yDescriptionAndDismissInvokesCallback() {
        val configuration = Configuration(composeTestRule.activity.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val context = composeTestRule.activity.createConfigurationContext(configuration)
        val band = widgetBandFor(DayRiskState.GoalIncomplete(DailyGoal.MEDITATION), "direct")
        val message = context.getString(requireNotNull(band.messageRes))
        var dismissed = 0
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
                MaterialTheme { DayRiskBannerContent(band = band, onDismiss = { dismissed++ }) }
            }
        }

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.widget_band_a11y, message))
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.day_risk_banner_dismiss_content_description),
        ).performClick()
        assertEquals(1, dismissed)
    }
}
