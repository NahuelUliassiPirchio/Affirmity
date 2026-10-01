package com.pirxhio.affirmity.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.CollectionNameResult
import com.pirxhio.affirmity.data.UserCollectionUi
import com.pirxhio.affirmity.ui.theme.GroupHighlight

/**
 * "Save to" sheet for one affirmation (opened by the card's + button). Favorites is pinned first,
 * then the user's groups ([saveToRows]); each row toggles immediately and the sheet stays open until
 * "Done", so one affirmation can go to several places. Stateless about data: the caller passes the
 * collections, the ids this affirmation already belongs to, its favourite state and callbacks.
 * "New group" opens [NewGroupSheet] (no "Start with": the affirmation is the seed) and the caller's
 * [onCreate] (name and highlight) is expected to add this affirmation to the new group.
 *
 * When [canCreate] is false (Free user at the limit) the create action is replaced by the limit
 * message and an optional upgrade button ([onUpgrade]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionPickerSheet(
    collections: List<UserCollectionUi>,
    memberIds: Set<String>,
    isFavorite: Boolean,
    canCreate: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleGroup: (userCollectionId: String) -> Unit,
    onCreate: suspend (name: String, highlightId: String) -> CollectionNameResult,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by remember { mutableStateOf(false) }
    val createLabel = stringResource(R.string.collection_picker_create)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.collection_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp).semantics { heading() },
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.collection_picker_done)) }
            }
            // The list scrolls inside a bounded region; the create / limit footer below stays
            // pinned and reachable with many collections or a large font scale.
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(saveToRows(collections, memberIds, isFavorite), key = { row ->
                    when (row) {
                        is SaveToRow.Favorites -> "favorites"
                        is SaveToRow.Group -> row.id
                    }
                }) { row ->
                    SaveToRowItem(
                        row = row,
                        onToggle = {
                            when (val action = row.toggle()) {
                                SaveToAction.ToggleFavorite -> onToggleFavorite()
                                is SaveToAction.ToggleGroup -> onToggleGroup(action.groupId)
                            }
                        },
                    )
                }
            }
            if (canCreate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = createLabel, role = Role.Button) { creating = true }
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

private val RowCoverSize = 24.dp
private val RowCoverCorner = 6.dp

@Composable
private fun SaveToRowItem(row: SaveToRow, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = row.checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row owns the toggle semantics (checked state + Checkbox role); the box is display-only.
        Checkbox(checked = row.checked, onCheckedChange = null)
        when (row) {
            is SaveToRow.Favorites -> {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 18.dp).size(14.dp),
                )
                Text(
                    text = stringResource(R.string.collection_picker_favorites),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            is SaveToRow.Group -> {
                GroupCover(
                    name = null,
                    highlight = GroupHighlight.fromId(row.highlightId),
                    size = RowCoverSize,
                    cornerRadius = RowCoverCorner,
                    modifier = Modifier.padding(start = 18.dp),
                )
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}
