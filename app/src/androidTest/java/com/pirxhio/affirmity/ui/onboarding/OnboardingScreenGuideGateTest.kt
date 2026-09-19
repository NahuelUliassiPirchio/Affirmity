package com.pirxhio.affirmity.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.auth.AuthState
import com.pirxhio.affirmity.personalization.goals.OnboardingAnswers
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenGuideGateTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun introIsTheFirstSurfaceForAnUnseenGuide() {
        composeTestRule.setContent {
            onboardingScreen()
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_intro_title),
        ).assertExists()
        composeTestRule.onNodeWithText(onboardingQuestions.first().question).assertDoesNotExist()
    }

    @Test
    fun startingTheSurveyRequestsTheGuideAndAdvancesToQuestions() {
        var surveyRequests = 0
        composeTestRule.setContent {
            onboardingScreen(onStartSurvey = { surveyRequests++ })
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_intro_start_button),
        ).performClick()

        assertEquals(1, surveyRequests)
        composeTestRule.onNodeWithText(onboardingQuestions.first().question).assertExists()
    }

    @Test
    fun persistedGuideCompletionResumesAtQuestionsWithoutReplayingIntro() {
        composeTestRule.setContent {
            onboardingScreen(resumeAtQuestions = true)
        }

        composeTestRule.onNodeWithText(onboardingQuestions.first().question).assertExists()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_intro_title),
        ).assertDoesNotExist()
    }

    @Test
    fun signedInPersistedResumeCompletesAfterLastQuestionWithoutRenderingFinalAuth() {
        val completionEvents = mutableListOf<String>()
        composeTestRule.setContent {
            onboardingScreen(
                authState = signedInState(),
                resumeAtQuestions = true,
                onSurveyCompleted = { completionEvents += "saved" },
                onFinished = { completionEvents += "finished" },
            )
        }

        onboardingQuestions.forEach { question ->
            composeTestRule.onNodeWithText(question.options.first().label).performClick()
            composeTestRule.onNodeWithText(
                composeTestRule.activity.getString(R.string.onboarding_next_button),
            ).performClick()
        }

        composeTestRule.waitForIdle()
        assertEquals(listOf("saved", "finished"), completionEvents)
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_auth_title),
        ).assertDoesNotExist()
    }

    @Test
    fun returningAccountFinishesWithoutRequestingTheGuide() {
        val authState = mutableStateOf<AuthState>(AuthState.SignedOut)
        var finishCount = 0
        var surveyRequests = 0
        var persistenceCount = 0
        composeTestRule.setContent {
            onboardingScreen(
                authState = authState.value,
                onSignInClicked = { authState.value = signedInState() },
                onFinished = { finishCount++ },
                onCheckReturningAccount = { true },
                onSurveyCompleted = { persistenceCount++ },
                onStartSurvey = { surveyRequests++ },
            )
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_intro_sign_in_button),
        ).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, finishCount)
        assertEquals(0, surveyRequests)
        assertEquals(0, persistenceCount)
    }

    @Test
    fun signedInNewAccountRequestsTheGuideAndProceedsToQuestions() {
        val authState = mutableStateOf<AuthState>(AuthState.SignedOut)
        var finishCount = 0
        var surveyRequests = 0
        composeTestRule.setContent {
            onboardingScreen(
                authState = authState.value,
                onSignInClicked = { authState.value = signedInState() },
                onFinished = { finishCount++ },
                onCheckReturningAccount = { false },
                onStartSurvey = { surveyRequests++ },
            )
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_intro_sign_in_button),
        ).performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, finishCount)
        assertEquals(1, surveyRequests)
        composeTestRule.onNodeWithText(onboardingQuestions.first().question).assertExists()
    }

    @Test
    fun answeringOneOrTwoQuestionsDoesNotPersistPartialSurveyAnswers() {
        var persistenceCount = 0
        composeTestRule.setContent {
            onboardingScreen(
                resumeAtQuestions = true,
                onSurveyCompleted = { persistenceCount++ },
            )
        }

        repeat(2) { questionIndex ->
            val question = onboardingQuestions[questionIndex]
            composeTestRule.onNodeWithText(question.options.first().label).performClick()
            composeTestRule.onNodeWithText(
                composeTestRule.activity.getString(R.string.onboarding_next_button),
            ).performClick()
            composeTestRule.waitForIdle()
            assertEquals(0, persistenceCount)
        }
    }

    @Test
    fun signingInAfterTheSurveyPersistsAnswersBeforeFinishing() {
        val authState = mutableStateOf<AuthState>(AuthState.SignedOut)
        val completionEvents = mutableListOf<String>()
        composeTestRule.setContent {
            onboardingScreen(
                authState = authState.value,
                resumeAtQuestions = true,
                onSignInClicked = { authState.value = signedInState() },
                onSurveyCompleted = { completionEvents += "saved" },
                onFinished = { completionEvents += "finished" },
            )
        }

        onboardingQuestions.forEach { question ->
            composeTestRule.onNodeWithText(question.options.first().label).performClick()
            composeTestRule.onNodeWithText(
                composeTestRule.activity.getString(R.string.onboarding_next_button),
            ).performClick()
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.settings_account_sign_in_button),
        ).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("saved", "finished"), completionEvents)
    }

    @Test
    fun suspendedFinalAuthPersistenceCannotBeTriggeredAgain() {
        val authState = mutableStateOf<AuthState>(AuthState.SignedOut)
        val persistenceRelease = CompletableDeferred<Unit>()
        var persistenceCount = 0
        var finishCount = 0
        composeTestRule.setContent {
            onboardingScreen(
                authState = authState.value,
                resumeAtQuestions = true,
                onSignInClicked = { authState.value = signedInState() },
                onSurveyCompleted = {
                    persistenceCount++
                    persistenceRelease.await()
                },
                onFinished = { finishCount++ },
            )
        }

        onboardingQuestions.forEach { question ->
            composeTestRule.onNodeWithText(question.options.first().label).performClick()
            composeTestRule.onNodeWithText(
                composeTestRule.activity.getString(R.string.onboarding_next_button),
            ).performClick()
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.settings_account_sign_in_button),
        ).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, persistenceCount)
        assertEquals(0, finishCount)
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_continue_without_account_button),
        ).assertIsNotEnabled()

        persistenceRelease.complete(Unit)
        composeTestRule.waitForIdle()
        assertEquals(1, finishCount)
    }

    @Test
    fun controlsExposeSingleChoiceReplacementMultiChoiceCapAndEmptyNextGate() {
        composeTestRule.setContent { onboardingScreen(resumeAtQuestions = true) }
        val next = composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_next_button),
        )

        next.assertIsNotEnabled()
        onboardingQuestions[0].options.take(3).forEach {
            composeTestRule.onNodeWithText(it.label).performClick()
        }
        composeTestRule.onNodeWithText(onboardingQuestions[0].options[3].label).assertIsNotEnabled()
        composeTestRule.onNodeWithText(onboardingQuestions[0].options[0].label).performClick()
        composeTestRule.onNodeWithText(onboardingQuestions[0].options[3].label).assertIsEnabled()
        next.performClick()

        composeTestRule.onNodeWithText("Suave").performClick().assertIsSelected()
        composeTestRule.onNodeWithText("Directo").performClick().assertIsSelected()
        composeTestRule.onNodeWithText("Suave").assertIsNotSelected()
        next.performClick()

        composeTestRule.onNodeWithText("Antes de dormir").performClick()
        next.assertIsEnabled()
    }

    @Test
    fun suspendingPersistenceDisablesRepeatedFinalCompletion() {
        val persistenceRelease = CompletableDeferred<Unit>()
        var persistenceCount = 0
        var finishCount = 0
        composeTestRule.setContent {
            onboardingScreen(
                authState = signedInState(),
                resumeAtQuestions = true,
                onSurveyCompleted = {
                    persistenceCount++
                    persistenceRelease.await()
                },
                onFinished = { finishCount++ },
            )
        }

        onboardingQuestions.forEach { question ->
            composeTestRule.onNodeWithText(question.options.first().label).performClick()
            val next = composeTestRule.onNodeWithText(
                composeTestRule.activity.getString(R.string.onboarding_next_button),
            )
            next.performClick()
            if (question == onboardingQuestions.last()) next.assertIsNotEnabled()
        }
        composeTestRule.waitForIdle()
        assertEquals(1, persistenceCount)
        assertEquals(0, finishCount)

        persistenceRelease.complete(Unit)
        composeTestRule.waitForIdle()
        assertEquals(1, finishCount)
    }

    @Test
    fun failedPersistenceReenablesFinalCompletionForRetry() {
        var persistenceCount = 0
        var finishCount = 0
        composeTestRule.setContent {
            onboardingScreen(
                authState = signedInState(),
                resumeAtQuestions = true,
                onSurveyCompleted = {
                    persistenceCount++
                    if (persistenceCount == 1) error("save failed")
                },
                onFinished = { finishCount++ },
            )
        }

        onboardingQuestions.forEach { question ->
            composeTestRule.onNodeWithText(question.options.first().label).performClick()
            composeTestRule.onNodeWithText(
                composeTestRule.activity.getString(R.string.onboarding_next_button),
            ).performClick()
        }
        composeTestRule.waitForIdle()

        val retry = composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_next_button),
        )
        assertEquals(1, persistenceCount)
        assertEquals(0, finishCount)
        retry.assertIsEnabled().performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, persistenceCount)
        assertEquals(1, finishCount)
    }

    @androidx.compose.runtime.Composable
    private fun onboardingScreen(
        authState: AuthState = AuthState.SignedOut,
        resumeAtQuestions: Boolean = false,
        onSignInClicked: () -> Unit = {},
        onFinished: () -> Unit = {},
        onCheckReturningAccount: suspend (String) -> Boolean = { false },
        onSurveyCompleted: suspend (OnboardingAnswers) -> Unit = {},
        onStartSurvey: () -> Unit = {},
    ) {
        OnboardingScreen(
            authState = authState,
            authError = null,
            onSignInClicked = onSignInClicked,
            onFinished = onFinished,
            onCheckReturningAccount = onCheckReturningAccount,
            onSurveyCompleted = onSurveyCompleted,
            resumeAtQuestions = resumeAtQuestions,
            onStartSurvey = onStartSurvey,
        )
    }

    private fun signedInState() = AuthState.SignedIn(
        uid = "returning-user",
        displayName = null,
        email = null,
    )
}
