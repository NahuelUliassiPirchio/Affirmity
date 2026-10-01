package com.pirxhio.affirmity.ui.collections

import androidx.annotation.StringRes
import androidx.compose.runtime.saveable.Saver
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.CollectionNameResult
import com.pirxhio.affirmity.data.UserCollectionUi

/*
 * Pure UI decisions for user collections. Composables only render these values, so a redesigned
 * UI can keep the same rules by binding to the same functions.
 */

/** String resource for a failed name check, or null when the result is [CollectionNameResult.Ok]. */
@StringRes
internal fun CollectionNameResult.errorStringRes(): Int? = when (this) {
    is CollectionNameResult.Ok -> null
    CollectionNameResult.Blank -> R.string.collection_name_error_blank
    CollectionNameResult.TooLong -> R.string.collection_name_error_too_long
    CollectionNameResult.Duplicate -> R.string.collection_name_error_duplicate
    CollectionNameResult.LimitReached -> R.string.collection_limit_reached
}

/** One line of the collection picker: whether the affirmation is already a member. */
internal data class PickerRow(val id: String, val name: String, val checked: Boolean)

internal enum class PickerToggle { Add, Remove }

internal fun PickerRow.toggle(): PickerToggle = if (checked) PickerToggle.Remove else PickerToggle.Add

/** Picker rows in collection (chip) order; [memberIds] ids that match no collection are ignored. */
internal fun pickerRows(collections: List<UserCollectionUi>, memberIds: Set<String>): List<PickerRow> =
    collections.map { PickerRow(id = it.id, name = it.name, checked = it.id in memberIds) }

/** Chip text: the name plus how many existing affirmations the collection resolves to. */
internal fun collectionChipLabel(collection: UserCollectionUi): String =
    "${collection.name} (${collection.resolvedItemCount})"

/** Which message to show when the feed has no affirmations. */
enum class FeedEmptyState { None, Collections, Generic }

/**
 * An empty feed points to collections whenever the user owns any (all switched off, or enabled
 * but with nothing visible); the generic add-an-affirmation copy is only for users with none.
 * An enabled collection implies one exists, so [hasCollections] alone covers both cases.
 */
fun feedEmptyState(
    feedIsEmpty: Boolean,
    hasCollections: Boolean,
): FeedEmptyState = when {
    !feedIsEmpty -> FeedEmptyState.None
    hasCollections -> FeedEmptyState.Collections
    else -> FeedEmptyState.Generic
}

@StringRes
fun FeedEmptyState.messageRes(): Int = when (this) {
    FeedEmptyState.Collections -> R.string.feed_empty_collections
    FeedEmptyState.Generic -> R.string.feed_empty_generic
    // The empty-feed branch is only rendered for an empty feed, where feedEmptyState never returns
    // None. Fail loudly instead of silently showing the wrong copy if that contract is broken.
    FeedEmptyState.None -> error("FeedEmptyState.None has no message: the feed is not empty")
}

/** Long-press management flow of a chip: actions, then rename or a delete confirmation. */
internal sealed interface CollectionManageState {
    data object Closed : CollectionManageState
    data class Actions(val collection: UserCollectionUi) : CollectionManageState
    data class Renaming(val collection: UserCollectionUi) : CollectionManageState
    data class ConfirmingDelete(val collection: UserCollectionUi) : CollectionManageState
}

internal sealed interface CollectionManageEvent {
    data class LongPress(val collection: UserCollectionUi) : CollectionManageEvent
    data object ChooseRename : CollectionManageEvent
    data object ChooseDelete : CollectionManageEvent
    data object ConfirmDelete : CollectionManageEvent
    data object Dismiss : CollectionManageEvent
}

internal fun CollectionManageState.reduce(event: CollectionManageEvent): CollectionManageState = when (event) {
    is CollectionManageEvent.LongPress -> CollectionManageState.Actions(event.collection)
    CollectionManageEvent.ChooseRename -> when (this) {
        is CollectionManageState.Actions -> CollectionManageState.Renaming(collection)
        else -> this
    }
    CollectionManageEvent.ChooseDelete -> when (this) {
        is CollectionManageState.Actions -> CollectionManageState.ConfirmingDelete(collection)
        else -> this
    }
    CollectionManageEvent.ConfirmDelete, CollectionManageEvent.Dismiss -> CollectionManageState.Closed
}

/**
 * Saver so the management flow survives rotation/recreation (same lifetime as the picker's
 * `rememberSaveable` affirmation id). Stores only primitives: kind plus the chip's fields.
 */
internal val CollectionManageStateSaver: Saver<CollectionManageState, Any> = Saver(
    save = { state ->
        when (state) {
            CollectionManageState.Closed -> listOf(KIND_CLOSED)
            is CollectionManageState.Actions -> state.collection.toSaved(KIND_ACTIONS)
            is CollectionManageState.Renaming -> state.collection.toSaved(KIND_RENAMING)
            is CollectionManageState.ConfirmingDelete -> state.collection.toSaved(KIND_CONFIRMING_DELETE)
        }
    },
    restore = { saved ->
        val list = saved as List<*>
        val kind = list[0] as String
        if (kind == KIND_CLOSED) {
            CollectionManageState.Closed
        } else {
            val collection = UserCollectionUi(
                id = list[1] as String,
                name = list[2] as String,
                enabled = list[3] as Boolean,
                resolvedItemCount = list[4] as Int,
            )
            when (kind) {
                KIND_ACTIONS -> CollectionManageState.Actions(collection)
                KIND_RENAMING -> CollectionManageState.Renaming(collection)
                else -> CollectionManageState.ConfirmingDelete(collection)
            }
        }
    },
)

private const val KIND_CLOSED = "closed"
private const val KIND_ACTIONS = "actions"
private const val KIND_RENAMING = "renaming"
private const val KIND_CONFIRMING_DELETE = "confirming_delete"

private fun UserCollectionUi.toSaved(kind: String): List<Any> = listOf(kind, id, name, enabled, resolvedItemCount)
