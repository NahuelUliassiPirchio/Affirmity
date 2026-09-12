package com.pirxhio.affirmity.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.personalization.goals.OnboardingAnswers
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

        composeTestRule.onNodeWithText("Fortalecer mi confianza").performClick()
        composeTestRule.runOnIdle { assertEquals(0, store.saveCalls) }

        composeTestRule.onNodeWithText("Actualizar mis objetivos").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { store.saveCalls == 1 }

        assertEquals(setOf("calm", "confidence"), store.savedGoalIds)
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

    override suspend fun saveGoalIds(ids: Set<String>) {
        saveCalls++
        savedGoalIds = ids
        goals.value = ids
    }

    override suspend fun saveOnboardingAnswers(answers: OnboardingAnswers) {
        goals.value = answers.goalIds
    }
}
