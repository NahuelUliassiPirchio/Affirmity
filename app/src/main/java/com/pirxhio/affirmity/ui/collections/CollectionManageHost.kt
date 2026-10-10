package com.pirxhio.affirmity.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.CollectionNameResult
import com.pirxhio.affirmity.data.CopySuffixes
import com.pirxhio.affirmity.data.DefaultGroups
import kotlinx.coroutines.launch

/**
 * Renders the long-press flow ([CollectionManageState]) for chips: an actions sheet (Rename,
 * Delete for user groups; Duplicate and the feed switch for read-only default groups), the rename
 * name dialog, and the delete confirmation. State lives with the caller; [onDelete] is only ever
 * invoked from the confirmation stage. [enabledFor] reads the LIVE enabled flag so the switch
 * follows the toggle while the sheet stays open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionManageHost(
    savedState: CollectionManageState,
    onEvent: (CollectionManageEvent) -> Unit,
    onRename: suspend (userCollectionId: String, name: String) -> CollectionNameResult,
    onDelete: (userCollectionId: String) -> Unit,
    canCreate: Boolean,
    enabledFor: (userCollectionId: String) -> Boolean,
    onToggle: (userCollectionId: String) -> Unit,
    onDuplicate: suspend (defaultGroupId: String, name: String, suffixes: CopySuffixes) -> CollectionNameResult,
    onUpgrade: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val defaultNames = DefaultGroups.all.associate { it.id to stringResource(it.nameRes) }
    val state = savedState.withLocalizedDefaultName(defaultNames::get)
    val suffixes = CopySuffixes(
        first = stringResource(R.string.groups_duplicate_copy_suffix),
        numbered = stringResource(R.string.groups_duplicate_copy_suffix_numbered),
    )
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
                manageActionsFor(state.collection).forEach { action ->
                    when (action) {
                        ManageAction.Rename -> CollectionActionRow(
                            icon = Icons.Filled.Edit,
                            label = stringResource(R.string.collection_chip_action_rename),
                            onClick = { onEvent(CollectionManageEvent.ChooseRename) },
                        )
                        ManageAction.Delete -> CollectionActionRow(
                            icon = Icons.Filled.Delete,
                            label = stringResource(R.string.collection_chip_action_delete),
                            onClick = { onEvent(CollectionManageEvent.ChooseDelete) },
                        )
                        ManageAction.Duplicate -> {
                            val limited = duplicateTap(canCreate) == DuplicateTap.Upgrade
                            CollectionActionRow(
                                icon = Icons.Filled.ContentCopy,
                                label = stringResource(R.string.groups_action_duplicate),
                                supporting = if (limited) stringResource(R.string.collection_limit_reached) else null,
                                onClick = {
                                    when (duplicateTap(canCreate)) {
                                        DuplicateTap.Upgrade -> {
                                            onEvent(CollectionManageEvent.Dismiss)
                                            onUpgrade()
                                        }
                                        DuplicateTap.Duplicate -> scope.launch {
                                            val verdict = onDuplicate(state.collection.id, state.collection.name, suffixes)
                                            if (verdict is CollectionNameResult.Ok) onEvent(CollectionManageEvent.Dismiss)
                                        }
                                    }
                                },
                            )
                        }
                        ManageAction.ToggleInFeed -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.groups_action_in_feed),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = enabledFor(state.collection.id),
                                onCheckedChange = { onToggle(state.collection.id) },
                            )
                        }
                    }
                }
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
private fun CollectionActionRow(icon: ImageVector, label: String, onClick: () -> Unit, supporting: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.padding(start = 18.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
