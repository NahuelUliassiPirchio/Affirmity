package com.pirxhio.affirmity.ui.settings

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import androidx.activity.ComponentActivity
import com.pirxhio.affirmity.R
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.personalization.goals.DivergenceState
import com.pirxhio.affirmity.personalization.goals.OnboardingAnswers
import com.pirxhio.affirmity.personalization.goals.UserGoal
import com.pirxhio.affirmity.personalization.goals.UserGoalsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyGoalsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun editingIsBatchedUntilSaveAndThenPersistsThroughTheStore() {
        val store = RecordingGoalsStore(setOf("calm"))
        composeTestRule.setContent { MyGoalsScreen(store = store) }

        val confidenceLabel = composeTestRule.activity.getString(
            UserGoal.all.first { it.id == "confidence" }.labelRes,
        )
        composeTestRule.onNodeWithText(confidenceLabel).performClick()
        composeTestRule.runOnIdle { assertEquals(0, store.saveCalls) }

        val saveLabel = composeTestRule.activity.getString(R.string.my_goals_save_button)
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(saveLabel))
        composeTestRule.onNodeWithText(saveLabel).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { store.saveCalls == 1 }

        assertEquals(setOf("calm", "confidence"), store.savedGoalIds)
    }

    @Test
    fun goalsAreShownInEnglishWhenLocaleIsEnglish() {
        composeTestRule.setContent {
            WithLocale("en", composeTestRule.activity) { MyGoalsScreen(store = RecordingGoalsStore(setOf("calm"))) }
        }

        composeTestRule.onNodeWithText("Find calm").assertExists()
        composeTestRule.onNodeWithText("Encontrar calma").assertDoesNotExist()
    }

    @Test
    fun goalsAreShownInSpanishWhenLocaleIsSpanish() {
        composeTestRule.setContent {
            WithLocale("es", composeTestRule.activity) { MyGoalsScreen(store = RecordingGoalsStore(setOf("calm"))) }
        }

        composeTestRule.onNodeWithText("Encontrar calma").assertExists()
        composeTestRule.onNodeWithText("Find calm").assertDoesNotExist()
    }
}

private class RecordingGoalsStore(initialGoalIds: Set<String>?) : UserGoalsStore {
    private val goals = MutableStateFlow(initialGoalIds)
    var saveCalls = 0
        private set
    var savedGoalIds: Set<String>? = null
        private set

    override fun observeGoalIds(): Flow<Set<String>?> = goals

    override fun observePreferredTone(): Flow<String?> = MutableStateFlow(null)

    override fun observeUsageMoments(): Flow<Set<String>?> = MutableStateFlow(null)

    override fun observeDivergenceState(): Flow<DivergenceState> = MutableStateFlow(DivergenceState())

    override suspend fun saveGoalIds(ids: Set<String>) {
        saveCalls++
        savedGoalIds = ids
        goals.value = ids
    }

    override suspend fun saveOnboardingAnswers(answers: OnboardingAnswers) {
        goals.value = answers.goalIds
    }

    override suspend fun recordDivergencePromptShown(atMillis: Long) = Unit

    override suspend fun recordDivergenceDismissal(goalId: String, atMillis: Long) = Unit
}

/** Renders [content] with an explicitly pinned locale so assertions never depend on the device language. */
@Composable
private fun WithLocale(language: String, activity: ComponentActivity, content: @Composable () -> Unit) {
    val configuration = Configuration(activity.resources.configuration).apply {
        setLocale(Locale.forLanguageTag(language))
    }
    CompositionLocalProvider(
        LocalContext provides activity.createConfigurationContext(configuration),
        LocalConfiguration provides configuration,
        content = content,
    )
}
