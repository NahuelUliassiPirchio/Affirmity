package com.pirxhio.affirmity.ui.collections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.CollectionNameResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Submit state shared by the name dialog and the new-group sheet. [submit] runs the authoritative
 * suspend verdict: Ok calls `onDone`, anything else is exposed as [errorRes] and the surface stays
 * open. `finally` re-enables the button however the call ends. Exceptions are deliberately NOT
 * caught: they (and cancellation) propagate; a swallowed repository error would look like a silent
 * no-op to the user.
 */
internal class NameSubmitState(private val scope: CoroutineScope) {
    var errorRes by mutableStateOf<Int?>(null)
        private set
    var submitting by mutableStateOf(false)
        private set

    fun clearError() {
        errorRes = null
    }

    fun submit(action: suspend () -> CollectionNameResult, onDone: () -> Unit) {
        submitting = true
        scope.launch {
            val result = try {
                action()
            } finally {
                submitting = false
            }
            errorRes = result.errorStringRes()
            if (errorRes == null) onDone()
        }
    }
}

@Composable
internal fun rememberNameSubmitState(): NameSubmitState {
    val scope = rememberCoroutineScope()
    return remember(scope) { NameSubmitState(scope) }
}

/** Free-tier limit message with the optional upgrade button, shared by the picker and new-group sheet. */
@Composable
internal fun CollectionLimitNotice(onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.collection_limit_reached),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onUpgrade) { Text(stringResource(R.string.collection_limit_upgrade)) }
    }
}
