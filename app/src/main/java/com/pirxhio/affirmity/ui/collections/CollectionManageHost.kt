package com.pirxhio.affirmity.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.CollectionNameResult

/**
 * Renders the long-press flow ([CollectionManageState]) for chips: an actions sheet (Rename,
 * Delete), the rename name dialog, and the delete confirmation. State lives with the caller;
 * [onDelete] is only ever invoked from the confirmation stage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionManageHost(
    state: CollectionManageState,
    onEvent: (CollectionManageEvent) -> Unit,
    onRename: suspend (userCollectionId: String, name: String) -> CollectionNameResult,
    onDelete: (userCollectionId: String) -> Unit,
) {
    when (state) {
        CollectionManageState.Closed -> Unit
        is CollectionManageState.Actions -> ModalBottomSheet(
            onDismissRequest = { onEvent(CollectionManageEvent.Dismiss) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    text = state.collection.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                CollectionActionRow(
                    icon = Icons.Filled.Edit,
                    label = stringResource(R.string.collection_chip_action_rename),
                    onClick = { onEvent(CollectionManageEvent.ChooseRename) },
                )
                CollectionActionRow(
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.collection_chip_action_delete),
                    onClick = { onEvent(CollectionManageEvent.ChooseDelete) },
                )
            }
        }
        is CollectionManageState.Renaming -> CollectionNameDialog(
            title = stringResource(R.string.collection_name_dialog_rename_title),
            confirmLabel = stringResource(R.string.collection_name_confirm_rename),
            initialName = state.collection.name,
            onSubmit = { name -> onRename(state.collection.id, name) },
            onDone = { onEvent(CollectionManageEvent.Dismiss) },
            onDismiss = { onEvent(CollectionManageEvent.Dismiss) },
        )
        is CollectionManageState.ConfirmingDelete -> AlertDialog(
            onDismissRequest = { onEvent(CollectionManageEvent.Dismiss) },
            title = { Text(stringResource(R.string.collection_delete_title)) },
            text = { Text(stringResource(R.string.collection_delete_message, state.collection.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(state.collection.id)
                    onEvent(CollectionManageEvent.ConfirmDelete)
                }) { Text(stringResource(R.string.collection_chip_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(CollectionManageEvent.Dismiss) }) {
                    Text(stringResource(R.string.collection_cancel))
                }
            },
        )
    }
}

// Same look as the feed's AffirmationActionRow (private there; promoting it is deferred).
@Composable
private fun CollectionActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 18.dp),
        )
    }
}
