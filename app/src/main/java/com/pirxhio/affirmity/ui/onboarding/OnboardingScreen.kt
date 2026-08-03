package com.pirxhio.affirmity.ui.onboarding

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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.auth.AuthError
import com.pirxhio.affirmity.auth.AuthState

/**
 * First-launch flow: an intro step ("I already have an account") to let a returning account skip
 * straight past the questions, then [onboardingQuestions] one at a time, then a final sign-in
 * step. Question answers are kept in memory only for now — nothing downstream reads them yet
 * (D: content/wiring TBD).
 *
 * Step numbering: 0 = intro, 1..[onboardingQuestions].size = questions, size+1 = final auth step
 * (skipped when the intro shortcut already recognized the account, per [skipFinalAuthStep]).
 */
@Composable
fun OnboardingScreen(
    authState: AuthState,
    authError: AuthError?,
    onSignInClicked: () -> Unit,
    onFinished: () -> Unit,
    onCheckReturningAccount: suspend (uid: String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var awaitingAccountCheck by rememberSaveable { mutableStateOf(false) }
    var skipFinalAuthStep by rememberSaveable { mutableStateOf(false) }
    val answers = remember { mutableStateMapOf<String, String>() }
    val lastQuestionStep = onboardingQuestions.size
    val totalSteps = onboardingQuestions.size + 2

    LaunchedEffect(authState) {
        if (authState !is AuthState.SignedIn) return@LaunchedEffect
        if (awaitingAccountCheck) {
            awaitingAccountCheck = false
            if (onCheckReturningAccount(authState.uid)) {
                onFinished()
            } else {
                skipFinalAuthStep = true
                step = 1
            }
        } else if (step == lastQuestionStep + 1) {
            onFinished()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
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
                },
                modifier = Modifier.weight(1f),
            )

            step <= lastQuestionStep -> {
                val question = onboardingQuestions[step - 1]
                QuestionStep(
                    question = question,
                    selectedOption = answers[question.id],
                    onOptionSelected = { answers[question.id] = it },
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
                                onFinished()
                            } else {
                                step += 1
                            }
                        },
                        enabled = answers.containsKey(question.id),
                    ) {
                        Text(stringResource(id = R.string.onboarding_next_button))
                    }
                } else {
                    TextButton(onClick = onFinished) {
                        Text(stringResource(id = R.string.onboarding_continue_without_account_button))
                    }
                }
            }
        }
    }
}

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
    selectedOption: String?,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = question.question, style = MaterialTheme.typography.headlineSmall)

        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            question.options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option == selectedOption,
                            onClick = { onOptionSelected(option) },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == selectedOption, onClick = { onOptionSelected(option) })
                    Text(text = option, modifier = Modifier.padding(start = 8.dp))
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
