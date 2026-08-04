package com.pirxhio.affirmity.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.auth.AuthState

/**
 * Small, de-emphasized "signed in as X" line + sign-out action, placed at the very bottom of
 * Settings so it reads as an exit hatch, not a primary account affordance.
 */
@Composable
fun SignOutSection(
    authState: AuthState.SignedIn,
    onSignOutClicked: () -> Unit,
    syncError: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val label = authState.email ?: authState.displayName ?: authState.uid
        Text(
            text = "${stringResource(id = R.string.settings_account_signed_in_prefix)} $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (syncError != null) {
            Text(
                text = stringResource(id = R.string.settings_account_sync_error),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        OutlinedButton(onClick = onSignOutClicked) {
            Text(
                text = stringResource(id = R.string.settings_account_sign_out_button),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
