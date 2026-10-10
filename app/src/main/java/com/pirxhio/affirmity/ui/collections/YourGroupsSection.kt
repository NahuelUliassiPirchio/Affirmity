package com.pirxhio.affirmity.ui.collections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import com.pirxhio.affirmity.ui.theme.GroupPlayAccent
import com.pirxhio.affirmity.ui.theme.GroupPlayOnAccent
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.pirxhio.affirmity.data.CopySuffixes
import com.pirxhio.affirmity.data.UserCollectionUi
import com.pirxhio.affirmity.ui.theme.GroupCoverGround
import com.pirxhio.affirmity.ui.theme.GroupCoverOnGround

private val CoverSize = 126.dp
private val PlayTargetSize = 48.dp
private val PlayCircleSize = 40.dp
private val PlayIconSize = 26.dp
private val PlayElevation = 6.dp
private val PlayRingWidth = 2.dp
// Target is 48dp around a 40dp circle, so a 4dp pad puts the circle 8dp from the cover corner.
private val PlayCornerInset = 4.dp
private const val PlayPressedScale = 0.92f
private const val PlayPressMillis = 90
private val ToggleIndicatorSize = 18.dp
private val ToggleCheckSize = 12.dp
private val ToggleRingWidth = 1.5.dp
private const val InFeedBorderAlpha = 0.5f

/**
 * "Your groups" shelf of "Your feed" (design 7a): one square cover per user collection followed by
 * the "New group" tile as the last entry of the scrolling row. Also owns the sheets it opens (group detail, new group) and the existing
 * rename/delete flow, so the host screen only passes data and callbacks in. Tapping a cover opens
 * its detail; the on/off switch lives there and goes through the same [onToggle] as the card toggle.
 */
@Composable
internal fun YourGroupsSection(
    allCollections: List<UserCollectionUi>,
    canCreate: Boolean,
    affirmationsFor: (userCollectionId: String) -> List<Affirmation>,
    onToggle: (userCollectionId: String) -> Unit,
    onRename: suspend (userCollectionId: String, name: String) -> CollectionNameResult,
    onDelete: (userCollectionId: String) -> Unit,
    onRemoveItem: (userCollectionId: String, affirmationId: String) -> Unit,
    onRestoreItem: (userCollectionId: String, affirmationId: String) -> Unit,
    onGroupRoundCompleted: (groupSize: Int) -> Unit,
    onCreate: suspend (name: String, highlightId: String) -> CollectionNameResult,
    onDuplicate: suspend (defaultGroupId: String, name: String, suffixes: CopySuffixes) -> CollectionNameResult,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Localize the default groups' placeholder names once, so the cards, the detail sheet and the
    // actions sheet all show the same locale-correct name (DG-6).
    val collections = allCollections.withLocalizedDefaultNames()
    // Ids, not objects: the sheets read the live collection so a toggle shows immediately, and a
    // deleted group simply stops resolving and closes its sheet. Saveable so rotation keeps them.
    var openGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    // Same request flag + guard + dialog as the detail sheet's "Play group", driven from the tile.
    var playingGroupId by rememberSaveable { mutableStateOf<String?>(null) }
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
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                groupsRowEntries(cards),
                key = { entry ->
                    when (entry) {
                        is GroupsRowEntry.Group -> entry.card.id
                        GroupsRowEntry.NewGroup -> "new"
                    }
                },
            ) { entry ->
                when (entry) {
                    is GroupsRowEntry.Group -> GroupCardTile(
                        card = entry.card,
                        onClick = { openGroupId = entry.card.id },
                        onToggle = { onToggle(entry.card.id) },
                        play = entry.tilePlay(),
                        onPlay = { playingGroupId = entry.card.id },
                    )
                    GroupsRowEntry.NewGroup -> NewGroupTile(onClick = { showNewGroup = true })
                }
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
            onRestoreItem = { affirmationId -> onRestoreItem(openCollection.id, affirmationId) },
            onRoundCompleted = onGroupRoundCompleted,
            onDismiss = { openGroupId = null },
        )
    }
    val playingCollection = collections.firstOrNull { it.id == playingGroupId }
    if (playingCollection != null) {
        val playable = affirmationsFor(playingCollection.id)
        if (shouldShowGroupPlayer(requested = true, itemCount = playable.size)) {
            GroupPlayerDialog(
                affirmations = playable,
                onRoundCompleted = onGroupRoundCompleted,
                onDismiss = { playingGroupId = null },
            )
        }
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
        savedState = manageState,
        onEvent = { event -> manageState = manageState.reduce(event) },
        onRename = onRename,
        onDelete = onDelete,
        canCreate = canCreate,
        enabledFor = { id -> allCollections.firstOrNull { it.id == id }?.enabled ?: false },
        onToggle = onToggle,
        onDuplicate = onDuplicate,
        onUpgrade = onUpgrade,
    )
}

@Composable
private fun NewGroupTile(onClick: () -> Unit) {
    val newGroupLabel = stringResource(R.string.groups_new_card_title)
    Column(modifier = Modifier.width(CoverSize)) {
        Box(
            modifier = Modifier
                .size(CoverSize)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .clickable(onClickLabel = newGroupLabel, role = Role.Button, onClick = onClick),
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
private fun GroupCardTile(
    card: GroupCardUi,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    play: TilePlay,
    onPlay: () -> Unit,
) {
    val inFeed = card.inFeed
    val openLabel = stringResource(R.string.groups_open_group_action)
    Column(modifier = Modifier.width(CoverSize)) {
        // The play button is a sibling of the clickable cover, not a child, so the two touch targets
        // never share a click; the cover's tap still opens the detail.
        Box {
            GroupCover(
                name = card.name,
                highlight = card.highlight,
                size = CoverSize,
                modifier = Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick),
                border = BorderStroke(
                    1.dp,
                    if (inFeed) card.highlight.color.copy(alpha = InFeedBorderAlpha) else MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            if (play != TilePlay.None) {
                GroupPlayButton(
                    name = card.name,
                    enabled = play == TilePlay.Enabled,
                    onClick = onPlay,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(PlayCornerInset),
                )
            }
        }
        GroupFeedToggle(name = card.name, inFeed = inFeed, onToggle = onToggle)
        Text(
            text = pluralStringResource(R.plurals.groups_affirmation_count, card.itemCount, card.itemCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Primary play control, Spotify-style: a solid 40dp accent circle with a near-black triangle, inset
 * 8dp from the cover's bottom-end corner and lifted by a soft shadow. The accent is the same for
 * every group (the group's colour stays on the cover). It sits inside the cover's bounds because a
 * child drawn outside its parent's bounds would not receive touches. The 48dp target wraps the
 * circle. When the group has nothing to play it stays in place but turns into a dim, flat, ghost
 * circle without a shadow, mirroring the disabled "Play group" button in the detail sheet.
 */
@Composable
private fun GroupPlayButton(
    name: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PlayPressedScale else 1f,
        animationSpec = tween(durationMillis = PlayPressMillis),
        label = "playPressScale",
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.size(PlayTargetSize),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(PlayCircleSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(if (enabled) Modifier.shadow(PlayElevation, CircleShape) else Modifier)
                .clip(CircleShape)
                .background(if (enabled) GroupPlayAccent else GroupCoverOnGround.copy(alpha = 0.16f))
                .border(PlayRingWidth, if (enabled) GroupCoverGround else Color.Transparent, CircleShape),
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.groups_tile_play_a11y, name),
                tint = if (enabled) GroupPlayOnAccent else GroupCoverOnGround.copy(alpha = 0.45f),
                modifier = Modifier.size(PlayIconSize),
            )
        }
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
