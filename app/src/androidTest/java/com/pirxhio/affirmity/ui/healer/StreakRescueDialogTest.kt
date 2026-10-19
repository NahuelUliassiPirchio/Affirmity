package com.pirxhio.affirmity.ui.healer

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreakRescueDialogTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setDialog(
        language: String,
        streakDays: Int,
        healerCount: Int,
        phase: RescuePhase,
        onApply: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onDone: () -> Unit = {},
    ) {
        val configuration = Configuration(composeTestRule.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        val localizedContext = composeTestRule.activity.createConfigurationContext(configuration)
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalResources provides localizedContext.resources,
                LocalConfiguration provides configuration,
            ) {
                MaterialTheme {
                    StreakRescueDialog(streakDays, healerCount, phase, onApply, onDismiss, onDone)
                }
            }
        }
    }

    private fun assertTexts(vararg texts: String) =
        texts.forEach { composeTestRule.onNodeWithText(it).assertIsDisplayed() }

    @Test
    fun offerRendersSpanishCopyWithPlurals() {
        setDialog("es", streakDays = 1, healerCount = 1, phase = RescuePhase.Offer)
                assertTexts(
            "Ayer se te pasó un día",
            "Tu racha de 1 día sigue en pie si usas un sanador hoy.",
            "Tienes 1 sanador guardado",
            "Solo cubre ayer. Hoy aún necesita su propia actividad.",
            "Usar sanador",
            "Ahora no",
        )
        composeTestRule.onNode(
            hasContentDescription("Anteayer completado, ayer sin actividad, hoy pendiente"),
        ).assertIsDisplayed()
    }

    @Test
    fun offerRendersSpanishCopyWithManyDaysAndHealers() {
        setDialog("es", streakDays = 12, healerCount = 2, phase = RescuePhase.Offer)
        assertTexts(
            "Tu racha de 12 días sigue en pie si usas un sanador hoy.",
            "Tienes 2 sanadores guardados",
        )
    }

    @Test
    fun offerRendersEnglishCopyWithPlurals() {
        setDialog("en", streakDays = 1, healerCount = 1, phase = RescuePhase.Offer)
        assertTexts(
            "Yesterday slipped by",
            "Your 1-day streak is still standing if you use a healer today.",
            "You have 1 saved healer",
            "It only covers yesterday. Today still needs its own activity.",
            "Use healer",
            "Not now",
        )
    }

    @Test
    fun offerRendersEnglishCopyWithManyDaysAndHealers() {
        setDialog("en", streakDays = 9, healerCount = 2, phase = RescuePhase.Offer)
        assertTexts(
            "Your 9-day streak is still standing if you use a healer today.",
            "You have 2 saved healers",
        )
    }

    @Test
    fun applyAndDismissButtonsInvokeTheirCallbacks() {
        var applied = 0
        var dismissed = 0
        setDialog(
            "en", 5, 1, RescuePhase.Offer,
            onApply = { applied++ }, onDismiss = { dismissed++ },
        )
        composeTestRule.onNodeWithText("Use healer").performClick()
        assertEquals(1, applied)
        assertEquals(0, dismissed)
        composeTestRule.onNodeWithText("Not now").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun mendedShowsRemainingCountsInSpanishIncludingZero() {
        setDialog("es", streakDays = 1, healerCount = 2, phase = RescuePhase.Mended)
        assertTexts(
            "Racha recuperada",
            "Tu 1 día sigue contando. Hoy aún necesita su propia actividad.",
            "Te queda 1 sanador",
            "Seguir",
        )
        composeTestRule.onNode(
            hasContentDescription("Anteayer completado, ayer sanado, hoy pendiente"),
        ).assertIsDisplayed()
    }

    @Test
    fun mendedShowsManyRemainingAndNoneLeft() {
        setDialog("es", streakDays = 6, healerCount = 3, phase = RescuePhase.Mended)
        assertTexts("Tus 6 días siguen contando. Hoy aún necesita su propia actividad.", "Te quedan 2 sanadores")
    }

    @Test
    fun mendedShowsZeroRemainingStringInBothLocales() {
        setDialog("es", streakDays = 4, healerCount = 1, phase = RescuePhase.Mended)
        assertTexts("No te quedan sanadores")
    }

    @Test
    fun mendedEnglishAndDoneCallback() {
        var done = 0
        setDialog("en", 4, 1, RescuePhase.Mended, onDone = { done++ })
        assertTexts(
            "Streak mended",
            "Your 4 days keep counting. Today still needs its own activity.",
            "No healers left",
        )
        composeTestRule.onNodeWithText("Continue").performClick()
        assertEquals(1, done)
    }

    @Test
    fun mendedEnglishRemainingPlurals() {
        setDialog("en", 3, 3, RescuePhase.Mended)
        assertTexts("2 healers left")
    }
}
