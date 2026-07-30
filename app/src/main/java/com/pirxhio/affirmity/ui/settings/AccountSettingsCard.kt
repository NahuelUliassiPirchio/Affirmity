package com.pirxhio.affirmity.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.auth.AuthError
import com.pirxhio.affirmity.auth.AuthState

/**
 * Stateless Settings account section — style-matched to `ChannelSettingsCard`. Sign-in/out is
 * always optional here; no other screen reads [authState] this stage.
 */
@Composable
fun AccountSettingsCard(
    authState: AuthState,
    authError: AuthError?,
    onSignInClicked: () -> Unit,
    onSignOutClicked: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(id = R.string.settings_account_label), style = MaterialTheme.typography.titleMedium)

            when (authState) {
                is AuthState.SignedOut -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = stringResource(id = R.string.settings_account_signed_out_text))
                        Button(onClick = onSignInClicked) {
                            Text(stringResource(id = R.string.settings_account_sign_in_button))
                        }
                    }
                }

                is AuthState.SignedIn -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val label = authState.email ?: authState.displayName ?: authState.uid
                        Text(text = "${stringResource(id = R.string.settings_account_signed_in_prefix)} $label")
                        TextButton(onClick = onSignOutClicked) {
                            Text(stringResource(id = R.string.settings_account_sign_out_button))
                        }
                    }
                }
            }

            if (authError != null) {
                Text(
                    text = authError.toDisplayMessage(),
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
