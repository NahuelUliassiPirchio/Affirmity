package com.pirxhio.affirmity.ui.onboarding

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.auth.AuthError
import com.pirxhio.affirmity.auth.AuthState
import com.pirxhio.affirmity.personalization.goals.OnboardingAnswers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * First-launch flow: an intro step ("I already have an account") to let a returning account skip
 * straight past the questions, then [onboardingQuestions] one at a time, then a final sign-in
 * step. Question answers stay in memory until the survey's final completion action, when
 * [onSurveyCompleted] persists the complete answer set before [onFinished] advances the app.
 *
 * Step numbering: 0 = intro, 1..[onboardingQuestions].size = questions, size+1 = final auth step
 * (skipped when the intro shortcut already recognized the account, per [skipFinalAuthStep]).
 * [onStartSurvey] lets the parent interpose a pre-survey gate after the intro without removing
 * this composable, while [resumeAtQuestions] restores the question step after that gate has been
 * persisted across process recreation.
 */
@Composable
fun OnboardingScreen(
    authState: AuthState,
    authError: AuthError?,
    onSignInClicked: () -> Unit,
    onFinished: () -> Unit,
    onCheckReturningAccount: suspend (uid: String) -> Boolean,
    onSurveyCompleted: suspend (OnboardingAnswers) -> Unit = {},
    resumeAtQuestions: Boolean = false,
    onStartSurvey: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable(resumeAtQuestions) {
        mutableIntStateOf(if (resumeAtQuestions) 1 else 0)
    }
    var awaitingAccountCheck by rememberSaveable { mutableStateOf(false) }
    // Not rememberSaveable: this only guards the in-flight completeOnce() coroutine in THIS
    // composition. Persisting it across process death left the Finish button permanently disabled
    // if the process died mid-save, with no coroutine left alive to ever reset it back to false.
    var completionInProgress by remember { mutableStateOf(false) }
    var completionError by remember { mutableStateOf(false) }
    var skipFinalAuthStep by rememberSaveable(resumeAtQuestions) {
        mutableStateOf(resumeAtQuestions && authState is AuthState.SignedIn)
    }
    val answers = remember { mutableStateMapOf<String, Set<String>>() }
    val coroutineScope = rememberCoroutineScope()
    val lastQuestionStep = onboardingQuestions.size
    val totalSteps = onboardingQuestions.size + 2

    suspend fun completeOnce() {
        if (completionInProgress) return
        completionInProgress = true
        completionError = false
        try {
            completeSurvey(answers, onSurveyCompleted, onFinished)
        } catch (cancellation: CancellationException) {
            completionInProgress = false
            throw cancellation
        } catch (error: Throwable) {
            Log.e(TAG, "onboarding survey completion failed", error)
            completionInProgress = false
            completionError = true
        }
    }

    LaunchedEffect(authState, resumeAtQuestions) {
        if (authState !is AuthState.SignedIn) return@LaunchedEffect
        if (resumeAtQuestions) skipFinalAuthStep = true
        if (awaitingAccountCheck) {
            awaitingAccountCheck = false
            if (onCheckReturningAccount(authState.uid)) {
                onFinished()
            } else {
                skipFinalAuthStep = true
                step = 1
                onStartSurvey()
            }
        } else if (step == lastQuestionStep + 1 && !completionInProgress) {
            completeOnce()
        }
    }

    // Bug 2a fix: this root previously had no Surface/background, so its Text fell back to
    // Compose's default black (instead of resolving through colorScheme.onBackground) and the
    // background stayed transparent, showing the Activity window's near-black theme behind it in
    // dark mode. Wrapping in a Surface keyed to colorScheme.background is the same pattern the
    // rest of the app's Scaffold-hosted screens already get through Material3.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            LinearProgressIndicator(
                progress = { (step + 1f) / totalSteps },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(24.dp))

            when {
                step == 0 -> IntroStep(
                    authError = authError,
                    awaitingAccountCheck = awaitingAccountCheck,
                    onSignInClicked = {
                        awaitingAccountCheck = true
                        onSignInClicked()
                    },
                    onStartClicked = {
                        awaitingAccountCheck = false
                        step = 1
                        onStartSurvey()
                    },
                    modifier = Modifier.weight(1f),
                )

                step <= lastQuestionStep -> {
                    val question = onboardingQuestions[step - 1]
                    QuestionStep(
                        question = question,
                        selectedOptions = answers[question.id].orEmpty(),
                        onOptionSelected = { optionId ->
                            answers[question.id] = toggleSelection(
                                current = answers[question.id].orEmpty(),
                                optionId = optionId,
                                maxSelections = question.maxSelections,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> AuthStep(
                    authState = authState,
                    authError = authError,
                    onSignInClicked = onSignInClicked,
                    modifier = Modifier.weight(1f),
                )
            }

            if (step > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { step -= 1 }) {
                        Text(stringResource(id = R.string.onboarding_back_button))
                    }

                    if (step <= lastQuestionStep) {
                        val question = onboardingQuestions[step - 1]
                        Button(
                            onClick = {
                                if (step == lastQuestionStep && skipFinalAuthStep) {
                                    coroutineScope.launch { completeOnce() }
                                } else {
                                    step += 1
                                }
                            },
                            enabled = answers[question.id].orEmpty().isNotEmpty() && !completionInProgress,
                        ) {
                            Text(stringResource(id = R.string.onboarding_next_button))
                        }
                    } else {
                        TextButton(onClick = {
                            coroutineScope.launch { completeOnce() }
                        }, enabled = !completionInProgress) {
                            Text(stringResource(id = R.string.onboarding_continue_without_account_button))
                        }
                    }
                }
            }

            if (completionError) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.onboarding_completion_error),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private const val TAG = "OnboardingScreen"

@Composable
private fun IntroStep(
    authError: AuthError?,
    awaitingAccountCheck: Boolean,
    onSignInClicked: () -> Unit,
    onStartClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(id = R.string.onboarding_intro_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.onboarding_intro_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onSignInClicked) {
            Text(stringResource(id = R.string.onboarding_intro_sign_in_button))
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onStartClicked) {
            Text(stringResource(id = R.string.onboarding_intro_start_button))
        }

        if (awaitingAccountCheck) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stringResource(id = R.string.onboarding_intro_checking))
        }

        if (authError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = authError.toDisplayMessage(), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun QuestionStep(
    question: OnboardingQuestion,
    selectedOptions: Set<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = question.question, style = MaterialTheme.typography.headlineSmall)

        Column(
            modifier = if (question.maxSelections == 1) Modifier.selectableGroup() else Modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            question.options.forEach { option ->
                val selected = option.id in selectedOptions
                val enabled = selected || selectedOptions.size < question.maxSelections
                Row(
                    modifier = (if (question.maxSelections == 1) {
                        Modifier.selectable(
                            selected = selected,
                            onClick = { onOptionSelected(option.id) },
                        )
                    } else {
                        Modifier.toggleable(
                            value = selected,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { onOptionSelected(option.id) },
                        )
                    }).fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (question.maxSelections == 1) {
                        RadioButton(selected = selected, onClick = { onOptionSelected(option.id) })
                    } else {
                        Checkbox(
                            checked = selected,
                            enabled = enabled,
                            onCheckedChange = { onOptionSelected(option.id) },
                        )
                    }
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(text = option.label)
                        option.example?.let { example ->
                            Text(text = example, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthStep(
    authState: AuthState,
    authError: AuthError?,
    onSignInClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(id = R.string.onboarding_auth_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.onboarding_auth_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))

        when (authState) {
            is AuthState.SignedOut -> Button(onClick = onSignInClicked) {
                Text(stringResource(id = R.string.settings_account_sign_in_button))
            }

            is AuthState.SignedIn -> Text(text = stringResource(id = R.string.onboarding_auth_signed_in))
        }

        if (authError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = authError.toDisplayMessage(), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AuthError.toDisplayMessage(): String = when (this) {
    AuthError.NoCredentialAvailable -> stringResource(id = R.string.settings_account_error_no_credential)
    AuthError.ProviderUnavailable -> stringResource(id = R.string.settings_account_error_provider_unavailable)
    AuthError.ConfigurationMissing -> stringResource(id = R.string.settings_account_error_config_missing)
    is AuthError.Unknown -> stringResource(id = R.string.settings_account_error_unknown)
}
