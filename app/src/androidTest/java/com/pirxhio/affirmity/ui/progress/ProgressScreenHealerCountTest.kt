package com.pirxhio.affirmity.ui.progress

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.data.HealerActivation
import com.pirxhio.affirmity.data.StreakHealerState
import com.pirxhio.affirmity.data.WeeklyStreak
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressScreenHealerCountTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun englishInventoryCountsRemainVisibleAcrossActivationStates() {
        assertInventoryCounts("en", "Saved healers", "Activate healer",
            "One healer activated for yesterday: your overall streak stays intact. Today still needs its own activity.")
    }

    @Test
    fun spanishInventoryCountsRemainVisibleAcrossActivationStates() {
        assertInventoryCounts("es", "Sanadores guardados", "Activar sanador",
            "Un sanador activado para ayer: tu racha general sigue intacta. Hoy aún necesita su propia actividad.")
    }

    private fun assertInventoryCounts(language: String, title: String, activate: String, used: String) {
        val state = mutableStateOf(healerState(0, HealerActivation.Unavailable))
        val configuration = Configuration(composeTestRule.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        val localizedContext = composeTestRule.activity.createConfigurationContext(configuration)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
            ) {
                MaterialTheme {
                    ProgressScreen(
                        affirmationsStreak = WeeklyStreak(emptyList(), 0),
                        meditationStreak = WeeklyStreak(emptyList(), 0),
                        streakHealer = state.value,
                        onActivateHealer = {},
                    )
                }
            }
        }

        // Zero and held inventory, an open activation window, and remaining inventory after use.
        val scenarios = listOf(
            healerState(0, HealerActivation.Unavailable),
            healerState(1, HealerActivation.Unavailable),
            healerState(2, HealerActivation.Unavailable),
            healerState(1, HealerActivation.Available(20676)),
            healerState(2, HealerActivation.Available(20676)),
            healerState(1, HealerActivation.UsedToday(20676)),
            healerState(0, HealerActivation.UsedToday(20676)),
        )
        for (scenario in scenarios) {
            composeTestRule.runOnIdle { state.value = scenario }
            composeTestRule.onNodeWithText("$title: ${scenario.healerCount} / 2").assertIsDisplayed()
            if (scenario.activation is HealerActivation.Available) {
                composeTestRule.onNodeWithText(activate).assertIsDisplayed()
            } else {
                composeTestRule.onNodeWithText(activate).assertDoesNotExist()
            }
            if (scenario.activation is HealerActivation.UsedToday) {
                composeTestRule.onNodeWithText(used).assertIsDisplayed()
            } else {
                composeTestRule.onNodeWithText(used).assertDoesNotExist()
            }
        }
    }

    private fun healerState(count: Int, activation: HealerActivation) = StreakHealerState(
        generalStreakDays = 4,
        isTodayDone = false,
        healerCount = count,
        pairProgress = 0,
        healedDays = if (activation is HealerActivation.UsedToday) setOf(activation.healedEpochDay) else emptySet(),
        activation = activation,
    )
}
