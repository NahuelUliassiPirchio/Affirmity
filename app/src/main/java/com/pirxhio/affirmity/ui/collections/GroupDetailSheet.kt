package com.pirxhio.affirmity.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.data.UserCollectionUi

/**
 * Group detail (design 7a): cover, name and count, the group's affirmations and an overflow button that opens the existing rename/delete flow.
 *
 * "Play group" opens [GroupPlayerDialog] over the sheet; the reorder handle is replaced by a remove
 * button because manual ordering doesn't exist in the business logic yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupDetailSheet(
    collection: UserCollectionUi,
    affirmations: List<Affirmation>,
    onMore: () -> Unit,
    onRemoveItem: (affirmationId: String) -> Unit,
    onRestoreItem: (affirmationId: String) -> Unit,
    onRoundCompleted: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val card = collection.toGroupCardUi()
    var playing by rememberSaveable { mutableStateOf(false) }
    // The app-level SnackbarHost sits behind modal sheets, so Undo lives in a host of its own here.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedMessage = stringResource(R.string.groups_detail_removed_snackbar)
    val undoLabel = stringResource(R.string.groups_detail_undo)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // One scrolling list for the whole sheet, so nothing collapses at large font scale or in
            // landscape.
            LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        GroupCover(name = null, highlight = card.highlight, size = 104.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.groups_detail_eyebrow).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                            )
                            Text(
                                text = pluralStringResource(R.plurals.groups_affirmation_count, card.itemCount, card.itemCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.groups_detail_close))
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        OutlinedButton(
                            onClick = { playing = true },
                            enabled = canPlayGroup(affirmations.size),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.groups_detail_play), modifier = Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = onMore) {
                            Icon(
                                Icons.Filled.MoreHoriz,
                                contentDescription = stringResource(R.string.groups_detail_more),
                            )
                        }
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.groups_detail_affirmations_title).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp)
                            .semantics { heading() },
                    )
                }
                if (affirmations.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.groups_detail_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
                items(affirmations, key = { it.id }) { affirmation ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) {
                        Text(
                            text = affirmation.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                        )
                        IconButton(
                            onClick = {
                                onRemoveItem(affirmation.id)
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = removedMessage,
                                        actionLabel = undoLabel,
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) onRestoreItem(affirmation.id)
                                }
                            },
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.groups_detail_remove_item_a11y, affirmation.title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
        if (shouldShowGroupPlayer(requested = playing, itemCount = affirmations.size)) {
            GroupPlayerDialog(
                affirmations = affirmations,
                onRoundCompleted = onRoundCompleted,
                onDismiss = { playing = false },
            )
        }
    }
}
