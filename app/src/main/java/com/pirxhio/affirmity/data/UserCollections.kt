package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
import java.util.Locale

/**
 * Domain model of a user collection, as returned by the repository. Kept free of Room types so
 * the pure rules (name validation, limits, feed resolution) can build on it without touching
 * persistence.
 */
data class UserCollection(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val enabled: Boolean,
    val lastUsedAtMillis: Long,
    val affirmationIds: List<String>,
)

/** UI-facing projection of a collection: [resolvedItemCount] only counts affirmations that still
 *  exist, so orphaned ids (e.g. a catalog row removed in a later release) never inflate it. */
data class UserCollectionUi(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val resolvedItemCount: Int,
)

/** Outcome of validating a collection name (and, for create, the tier limit). */
sealed interface CollectionNameResult {
    data class Ok(val name: String) : CollectionNameResult
    data object Blank : CollectionNameResult
    data object TooLong : CollectionNameResult
    data object Duplicate : CollectionNameResult
    data object LimitReached : CollectionNameResult
}

/** Free-tier cap on the number of collections; Pro is unlimited. */
const val FREE_COLLECTION_LIMIT = 2

/** Maximum collection name length, counted in Unicode code points after trimming. */
const val COLLECTION_NAME_MAX = 40

/**
 * Trims [raw] and checks it is non-blank, within [COLLECTION_NAME_MAX] code points, and unique
 * case-insensitively among [existing]. [excludingId] skips the collection being renamed so keeping
 * its own name is not a duplicate. Folding uses `lowercase(Locale.ROOT)` (not SQL NOCASE, which
 * only folds ASCII).
 */
internal fun validateCollectionName(
    raw: String,
    existing: List<UserCollection>,
    excludingId: String? = null,
): CollectionNameResult {
    val name = raw.trim()
    if (name.isEmpty()) return CollectionNameResult.Blank
    if (name.codePointCount(0, name.length) > COLLECTION_NAME_MAX) return CollectionNameResult.TooLong
    val key = name.lowercase(Locale.ROOT)
    val clash = existing.any { it.id != excludingId && it.name.trim().lowercase(Locale.ROOT) == key }
    return if (clash) CollectionNameResult.Duplicate else CollectionNameResult.Ok(name)
}

/**
 * Validation for a NEW collection: the tier limit is checked first (so a full Free user always
 * sees [CollectionNameResult.LimitReached], even for a blank name), then the name itself.
 */
internal fun validateNewCollection(
    raw: String,
    existing: List<UserCollection>,
    tier: AccessTier,
): CollectionNameResult =
    if (!canCreateUserCollection(tier, existing.size)) {
        CollectionNameResult.LimitReached
    } else {
        validateCollectionName(raw, existing)
    }

/** Projects collections to their chips, preserving order. [knownIds] is every affirmation id that
 *  currently exists (owned + catalog); orphan member ids are excluded from the count. */
internal fun List<UserCollection>.toUserCollectionUi(knownIds: Set<String>): List<UserCollectionUi> =
    map { collection ->
        UserCollectionUi(
            id = collection.id,
            name = collection.name,
            enabled = collection.enabled,
            resolvedItemCount = collection.affirmationIds.count { it in knownIds },
        )
    }

/** Free users may hold [FREE_COLLECTION_LIMIT] collections; Pro is unlimited. Only gates NEW
 *  creation: a downgraded user keeps (and can fully use) everything already created. */
internal fun canCreateUserCollection(tier: AccessTier, count: Int): Boolean =
    tier == AccessTier.PRO || count < FREE_COLLECTION_LIMIT

/**
 * Resolves the feed rows contributed by every ENABLED collection: members are looked up through
 * [byId] (orphans drop out), filtered by [eligible] (hidden / access / pre-resolution rules are
 * the caller's lambda, shared with the favorites segment), and de-duplicated by affirmation id so a
 * row in several collections appears once. Collection order, then member order, is preserved.
 */
internal fun <T> resolveEnabledCollectionRows(
    collections: List<UserCollection>,
    byId: (String) -> T?,
    eligible: (T) -> Boolean,
): List<T> =
    collections.asSequence()
        .filter { it.enabled }
        .flatMap { it.affirmationIds.asSequence() }
        .distinct()
        .mapNotNull(byId)
        .filter(eligible)
        .toList()
