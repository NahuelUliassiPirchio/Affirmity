package com.pirxhio.affirmity.ui.collections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.data.CollectionNameResult
import com.pirxhio.affirmity.data.UserCollectionUi

private val CoverSize = 126.dp
private val ToggleIndicatorSize = 18.dp
private val ToggleCheckSize = 12.dp
private val ToggleRingWidth = 1.5.dp
private const val InFeedBorderAlpha = 0.5f
private const val OutOfFeedBorderAlpha = 0.08f

/**
 * "Your groups" shelf of "Your feed" (design 7a): a "New group" tile followed by one square cover
 * per user collection. Also owns the sheets it opens (group detail, new group) and the existing
 * rename/delete flow, so the host screen only passes data and callbacks in. Tapping a cover opens
 * its detail; the on/off switch lives there and goes through the same [onToggle] as the card toggle.
 */
@Composable
internal fun YourGroupsSection(
    collections: List<UserCollectionUi>,
    canCreate: Boolean,
    affirmationsFor: (userCollectionId: String) -> List<Affirmation>,
    onToggle: (userCollectionId: String) -> Unit,
    onRename: suspend (userCollectionId: String, name: String) -> CollectionNameResult,
    onDelete: (userCollectionId: String) -> Unit,
    onRemoveItem: (userCollectionId: String, affirmationId: String) -> Unit,
    onCreate: suspend (name: String, highlightId: String) -> CollectionNameResult,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ids, not objects: the sheets read the live collection so a toggle shows immediately, and a
    // deleted group simply stops resolving and closes its sheet. Saveable so rotation keeps them.
    var openGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewGroup by rememberSaveable { mutableStateOf(false) }
    var manageState by rememberSaveable(stateSaver = CollectionManageStateSaver) {
        mutableStateOf<CollectionManageState>(CollectionManageState.Closed)
    }
    val cards = collections.toGroupCards()

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.groups_section_title),
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "new") { NewGroupTile(onClick = { showNewGroup = true }) }
            items(cards, key = { it.id }) { card ->
                GroupCardTile(
                    card = card,
                    onClick = { openGroupId = card.id },
                    onToggle = { onToggle(card.id) },
                )
            }
        }
    }

    val openCollection = collections.firstOrNull { it.id == openGroupId }
    if (openCollection != null) {
        GroupDetailSheet(
            collection = openCollection,
            affirmations = affirmationsFor(openCollection.id),
            onMore = { manageState = manageState.reduce(CollectionManageEvent.LongPress(openCollection)) },
            onRemoveItem = { affirmationId -> onRemoveItem(openCollection.id, affirmationId) },
            onDismiss = { openGroupId = null },
        )
    }
    if (showNewGroup) {
        NewGroupSheet(
            canCreate = canCreate,
            onCreate = onCreate,
            onUpgrade = onUpgrade,
            onDismiss = { showNewGroup = false },
        )
    }
    CollectionManageHost(
        state = manageState,
        onEvent = { event -> manageState = manageState.reduce(event) },
        onRename = onRename,
        onDelete = onDelete,
    )
}

@Composable
private fun NewGroupTile(onClick: () -> Unit) {
    Column(modifier = Modifier.width(CoverSize).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(CoverSize)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = stringResource(R.string.groups_new_card_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 9.dp),
        )
        Text(
            text = stringResource(R.string.groups_new_card_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupCardTile(card: GroupCardUi, onClick: () -> Unit, onToggle: () -> Unit) {
    val inFeed = card.inFeed
    Column(modifier = Modifier.width(CoverSize)) {
        GroupCover(
            name = card.name,
            highlight = card.highlight,
            size = CoverSize,
            modifier = Modifier.clickable(onClick = onClick),
            border = BorderStroke(
                1.dp,
                if (inFeed) card.highlight.color.copy(alpha = InFeedBorderAlpha) else Color.White.copy(alpha = OutOfFeedBorderAlpha),
            ),
        )
        GroupFeedToggle(name = card.name, inFeed = inFeed, onToggle = onToggle)
        Text(
            text = pluralStringResource(R.plurals.groups_affirmation_count, card.itemCount, card.itemCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The status line under a cover is the feed toggle: a 48dp switch-role target, so a group can be
 * turned on or off without opening it. The row is announced as one control carrying the group name
 * and its state; the indicator and text inside are decorative to accessibility.
 */
@Composable
private fun GroupFeedToggle(name: String, inFeed: Boolean, onToggle: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val statusColor = if (inFeed) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val statusText = stringResource(
        if (inFeed) R.string.groups_status_in_feed else R.string.groups_status_not_in_feed,
    )
    val label = stringResource(R.string.groups_toggle_a11y_label, name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = inFeed, role = Role.Switch, onValueChange = { onToggle() })
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = statusText
            },
    ) {
        ToggleIndicator(checked = inFeed, color = accent, ringColor = statusColor)
        Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
    }
}

@Composable
private fun ToggleIndicator(checked: Boolean, color: Color, ringColor: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(ToggleIndicatorSize)
            .clip(CircleShape)
            .then(
                if (checked) Modifier.background(color)
                else Modifier.border(ToggleRingWidth, ringColor, CircleShape),
            ),
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(ToggleCheckSize),
            )
        }
    }
}
