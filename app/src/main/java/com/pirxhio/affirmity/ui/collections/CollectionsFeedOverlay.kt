package com.pirxhio.affirmity.ui.collections

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pirxhio.affirmity.data.CollectionNameResult
import com.pirxhio.affirmity.data.UserCollectionUi

// Chips float over the feed at TopStart, this far down: that clears the favourite heart (24dp
// padding + 48dp button, TopStart) and sits below FloatingStatusOverlay (TopEnd, 16dp padding), so
// neither is covered.
private val ChipsTopOffset = 80.dp
private val ChipsHorizontalPadding = 24.dp

/**
 * The single entry point of the collections UI over the feed: the chips row, the chip management
 * flow (rename / delete) and the "add to collection" picker. Everything a designer may replace
 * lives behind this one composable; the host only keeps which affirmation the picker is open for
 * ([pickerAffirmationId]) and passes data and callbacks in.
 *
 * Chips are hidden in clean-screen mode and when the user has no collections. The management
 * state is `rememberSaveable` (via [CollectionManageStateSaver]) so, like the picker's id held by
 * the host, an open dialog survives rotation/recreation.
 */
@Composable
internal fun BoxScope.CollectionsFeedOverlay(
    collections: List<UserCollectionUi>,
    canCreate: Boolean,
    isCleanScreen: Boolean,
    pickerAffirmationId: String?,
    memberIdsFor: (affirmationId: String) -> Set<String>,
    onToggle: (userCollectionId: String) -> Unit,
    onRename: suspend (userCollectionId: String, name: String) -> CollectionNameResult,
    onDelete: (userCollectionId: String) -> Unit,
    onAdd: (userCollectionId: String, affirmationId: String) -> Unit,
    onRemove: (userCollectionId: String, affirmationId: String) -> Unit,
    onCreate: suspend (name: String, affirmationId: String) -> CollectionNameResult,
    onUpgrade: () -> Unit,
    onPickerDismiss: () -> Unit,
) {
    var manageState by rememberSaveable(stateSaver = CollectionManageStateSaver) {
        mutableStateOf<CollectionManageState>(CollectionManageState.Closed)
    }

    if (!isCleanScreen && collections.isNotEmpty()) {
        CollectionChipsRow(
            collections = collections,
            onToggle = onToggle,
            onLongPress = { collection -> manageState = manageState.reduce(CollectionManageEvent.LongPress(collection)) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = ChipsHorizontalPadding, end = ChipsHorizontalPadding, top = ChipsTopOffset),
        )
    }
    CollectionManageHost(
        state = manageState,
        onEvent = { event -> manageState = manageState.reduce(event) },
        onRename = onRename,
        onDelete = onDelete,
    )
    pickerAffirmationId?.let { affirmationId ->
        CollectionPickerSheet(
            collections = collections,
            memberIds = memberIdsFor(affirmationId),
            canCreate = canCreate,
            onAdd = { userCollectionId -> onAdd(userCollectionId, affirmationId) },
            onRemove = { userCollectionId -> onRemove(userCollectionId, affirmationId) },
            onCreate = { name -> onCreate(name, affirmationId) },
            onUpgrade = onUpgrade,
            onDismiss = onPickerDismiss,
        )
    }
}
