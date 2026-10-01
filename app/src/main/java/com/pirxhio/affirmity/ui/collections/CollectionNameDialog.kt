package com.pirxhio.affirmity.ui.collections

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.CollectionNameResult
import kotlinx.coroutines.launch

/**
 * Name input for creating or renaming a collection. [onSubmit] is a suspend call whose returned
 * [CollectionNameResult] is authoritative: Ok closes the dialog via [onDone]; anything else (even
 * a Duplicate or LimitReached that lost a race) is shown inline and the dialog stays open.
 */
@Composable
internal fun CollectionNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onSubmit: suspend (name: String) -> CollectionNameResult,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorRes = null
                },
                label = { Text(stringResource(R.string.collection_name_label)) },
                singleLine = true,
                isError = errorRes != null,
                supportingText = errorRes?.let { res -> { Text(stringResource(res)) } },
            )
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    submitting = true
                    scope.launch {
                        // finally re-enables the button however onSubmit ends, so a failure cannot
                        // leave it disabled forever. Exceptions are deliberately NOT caught: they
                        // (and cancellation) propagate as before; a swallowed repository error would
                        // look like a silent no-op to the user.
                        val result = try {
                            onSubmit(name)
                        } finally {
                            submitting = false
                        }
                        errorRes = result.errorStringRes()
                        if (errorRes == null) onDone()
                    }
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.collection_cancel))
            }
        },
    )
}
