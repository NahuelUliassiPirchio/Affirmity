package com.pirxhio.affirmity.ui.collections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.UserCollectionUi
import com.pirxhio.affirmity.ui.theme.GroupHighlight

/**
 * One toggleable chip per collection, in the order given (already chip order from the app state).
 * Tap toggles the collection; long press asks the caller to manage it (rename/delete).
 *
 * Built on [Surface] + [combinedClickable] rather than Material's `FilterChip`, because the chip
 * components own their click handling and expose no long press. The colours mirror a selected
 * `InputChip`/`FilterChip` (secondary container when on, outlined when off) so it reads like
 * `CatalogThemeChip`; a designer can swap this body freely, the callbacks are the contract.
 */
@Composable
internal fun CollectionChipsRow(
    collections: List<UserCollectionUi>,
    onToggle: (userCollectionId: String) -> Unit,
    onLongPress: (UserCollectionUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.collection_chips_content_description)
    Row(
        modifier = modifier
            .semantics { contentDescription = description }
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        collections.forEach { collection ->
            key(collection.id) {
                CollectionChip(
                    collection = collection,
                    onClick = { onToggle(collection.id) },
                    onLongClick = { onLongPress(collection) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionChip(
    collection: UserCollectionUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // The outer Box carries the 48dp touch target and all click semantics; the visible chip stays
    // compact inside it. Role.Switch + toggleableState announce on/off, `selected` keeps the state
    // queryable by tests and other services.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics {
                selected = collection.enabled
                toggleableState = ToggleableState(collection.enabled)
            }
            .combinedClickable(
                role = Role.Switch,
                onClickLabel = stringResource(R.string.collection_chip_click_label),
                onClick = onClick,
                onLongClickLabel = stringResource(R.string.collection_chip_long_click_label),
                onLongClick = onLongClick,
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (collection.enabled) colors.secondaryContainer else colors.surface,
            contentColor = if (collection.enabled) colors.onSecondaryContainer else colors.onSurfaceVariant,
            border = if (collection.enabled) null else BorderStroke(1.dp, colors.outline),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // The group's highlight as a tilted marker stroke, as on the design's chip rail.
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 8.dp)
                        .rotate(-8f)
                        .background(GroupHighlight.fromId(collection.highlightId).color, RoundedCornerShape(2.dp)),
                )
                Text(text = collectionChipLabel(collection), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
