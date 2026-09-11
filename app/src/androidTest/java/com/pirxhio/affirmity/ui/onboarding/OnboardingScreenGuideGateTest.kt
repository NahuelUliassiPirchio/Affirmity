package com.pirxhio.affirmity.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.auth.AuthState
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
        var finishCount = 0
        composeTestRule.setContent {
            onboardingScreen(
                authState = signedInState(),
                resumeAtQuestions = true,
                onFinished = { finishCount++ },
            )
        }

        onboardingQuestions.forEach { question ->
            composeTestRule.onNodeWithText(question.options.first()).performClick()
            composeTestRule.onNodeWithText(
                composeTestRule.activity.getString(R.string.onboarding_next_button),
            ).performClick()
        }

        composeTestRule.waitForIdle()
        assertEquals(1, finishCount)
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_auth_title),
        ).assertDoesNotExist()
    }

    @Test
    fun returningAccountFinishesWithoutRequestingTheGuide() {
        val authState = mutableStateOf<AuthState>(AuthState.SignedOut)
        var finishCount = 0
        var surveyRequests = 0
        composeTestRule.setContent {
            onboardingScreen(
                authState = authState.value,
                onSignInClicked = { authState.value = signedInState() },
                onFinished = { finishCount++ },
                onCheckReturningAccount = { true },
                onStartSurvey = { surveyRequests++ },
            )
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.onboarding_intro_sign_in_button),
        ).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, finishCount)
        assertEquals(0, surveyRequests)
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

    @androidx.compose.runtime.Composable
    private fun onboardingScreen(
        authState: AuthState = AuthState.SignedOut,
        resumeAtQuestions: Boolean = false,
        onSignInClicked: () -> Unit = {},
        onFinished: () -> Unit = {},
        onCheckReturningAccount: suspend (String) -> Boolean = { false },
        onStartSurvey: () -> Unit = {},
    ) {
        OnboardingScreen(
            authState = authState,
            authError = null,
            onSignInClicked = onSignInClicked,
            onFinished = onFinished,
            onCheckReturningAccount = onCheckReturningAccount,
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
