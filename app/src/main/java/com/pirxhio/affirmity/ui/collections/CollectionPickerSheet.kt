package com.pirxhio.affirmity.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.CollectionNameResult
import com.pirxhio.affirmity.data.UserCollectionUi

/**
 * "Add to collection" picker for one affirmation. Stateless about data: the caller passes the
 * collections, the ids this affirmation already belongs to, and callbacks. Tapping a row toggles
 * membership; "Create new collection" opens [NewGroupSheet] and, on success, the caller's
 * [onCreate] (name and highlight) is expected to add this affirmation to the new collection.
 *
 * When [canCreate] is false (Free user at the limit) the create action is replaced by the limit
 * message and an optional upgrade button ([onUpgrade]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionPickerSheet(
    collections: List<UserCollectionUi>,
    memberIds: Set<String>,
    canCreate: Boolean,
    onAdd: (userCollectionId: String) -> Unit,
    onRemove: (userCollectionId: String) -> Unit,
    onCreate: suspend (name: String, highlightId: String) -> CollectionNameResult,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(
                text = stringResource(R.string.collection_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val rows = pickerRows(collections, memberIds)
            if (rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.collection_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            // The list scrolls inside a bounded region; the create / limit footer below stays
            // pinned and reachable with many collections or a large font scale.
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(rows, key = { it.id }) { row ->
                    PickerRowItem(
                        row = row,
                        onToggle = {
                            when (row.toggle()) {
                                PickerToggle.Add -> onAdd(row.id)
                                PickerToggle.Remove -> onRemove(row.id)
                            }
                        },
                    )
                }
            }
            if (canCreate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { creating = true }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = stringResource(R.string.collection_picker_create),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 18.dp),
                    )
                }
            } else {
                CollectionLimitNotice(onUpgrade = onUpgrade, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
        }
    }

    if (creating) {
        // The affirmation is the seed, so the "Start with" choice is hidden.
        NewGroupSheet(
            canCreate = canCreate,
            onCreate = onCreate,
            onUpgrade = onUpgrade,
            onDismiss = { creating = false },
            showStartWith = false,
        )
    }
}

@Composable
private fun PickerRowItem(row: PickerRow, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = row.checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row owns the toggle semantics (checked state + Checkbox role); the box is display-only.
        Checkbox(checked = row.checked, onCheckedChange = null)
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 18.dp),
        )
    }
}
