package com.pirxhio.affirmity.ui.collections

import androidx.compose.runtime.Composable
import com.pirxhio.affirmity.data.CollectionNameResult
import com.pirxhio.affirmity.data.UserCollectionUi

/**
 * Entry point of the feed's "Save to" sheet. Group on/off and rename/delete live in "Your feed"
 * ([YourGroupsSection]); nothing about collections is drawn over the feed itself. The host only
 * keeps which affirmation the sheet is open for ([pickerAffirmationId]) and passes data and
 * callbacks in.
 */
@Composable
internal fun CollectionsFeedOverlay(
    collections: List<UserCollectionUi>,
    canCreate: Boolean,
    pickerAffirmationId: String?,
    isFavoriteFor: (affirmationId: String) -> Boolean,
    onToggleFavorite: (affirmationId: String) -> Unit,
    memberIdsFor: (affirmationId: String) -> Set<String>,
    onToggleInGroup: (userCollectionId: String, affirmationId: String) -> Unit,
    onCreate: suspend (name: String, highlightId: String, affirmationId: String) -> CollectionNameResult,
    onUpgrade: () -> Unit,
    onPickerDismiss: () -> Unit,
) {
    pickerAffirmationId?.let { affirmationId ->
        CollectionPickerSheet(
            collections = collections,
            memberIds = memberIdsFor(affirmationId),
            isFavorite = isFavoriteFor(affirmationId),
            onToggleFavorite = { onToggleFavorite(affirmationId) },
            canCreate = canCreate,
            onToggleGroup = { userCollectionId -> onToggleInGroup(userCollectionId, affirmationId) },
            onCreate = { name, highlightId -> onCreate(name, highlightId, affirmationId) },
            onUpgrade = onUpgrade,
            onDismiss = onPickerDismiss,
        )
    }
}
