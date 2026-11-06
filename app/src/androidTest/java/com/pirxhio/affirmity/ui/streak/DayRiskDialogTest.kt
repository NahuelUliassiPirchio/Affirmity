package com.pirxhio.affirmity.ui.streak

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.analytics.DailyGoal
import com.pirxhio.affirmity.data.DayRiskState
import com.pirxhio.affirmity.widget.WidgetBand
import com.pirxhio.affirmity.widget.widgetBandFor
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayRiskDialogTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val configuration by lazy {
        Configuration(composeTestRule.activity.resources.configuration).apply { setLocale(Locale.ENGLISH) }
    }
    private val context by lazy { composeTestRule.activity.createConfigurationContext(configuration) }

    private fun show(band: WidgetBand, target: DayRiskCtaTarget, onCta: () -> Unit, onLater: () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
                MaterialTheme { DayRiskDialogContent(band = band, ctaTarget = target, onCta = onCta, onLater = onLater) }
            }
        }
    }

    @Test
    fun showsMessageAndCtaInvokesItsCallback() {
        val band = widgetBandFor(DayRiskState.GoalIncomplete(DailyGoal.MEDITATION), "direct")
        var cta = 0
        var later = 0
        show(band, DayRiskCtaTarget.MEDITATION, onCta = { cta++ }, onLater = { later++ })

        composeTestRule.onNodeWithText(context.getString(requireNotNull(band.messageRes))).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.day_risk_dialog_cta_meditate)).performClick()
        assertEquals(1, cta)
        assertEquals(0, later)
    }

    @Test
    fun laterInvokesDismissOnly() {
        val band = widgetBandFor(DayRiskState.StreakAtRisk, "soft")
        var cta = 0
        var later = 0
        show(band, DayRiskCtaTarget.AFFIRMATIONS, onCta = { cta++ }, onLater = { later++ })

        composeTestRule.onNodeWithText(context.getString(R.string.day_risk_dialog_cta_affirmations)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.day_risk_dialog_later)).performClick()
        assertEquals(0, cta)
        assertEquals(1, later)
    }
}
