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
    val highlightId: String = DEFAULT_COLLECTION_HIGHLIGHT_ID,
    /** True for code-defined default groups merged in at read time (never persisted as collections). */
    val isDefault: Boolean = false,
)

/** UI-facing projection of a collection: [resolvedItemCount] only counts affirmations that still
 *  exist, so orphaned ids (e.g. a catalog row removed in a later release) never inflate it. */
data class UserCollectionUi(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val resolvedItemCount: Int,
    val highlightId: String = DEFAULT_COLLECTION_HIGHLIGHT_ID,
    val isDefault: Boolean = false,
)

/** Persisted id of the highlight a collection gets when none is chosen. Mirrors
 *  `GroupHighlight.Default.id` (asserted in a test) and the column's SQL default. */
const val DEFAULT_COLLECTION_HIGHLIGHT_ID = "teal"

/** Outcome of validating a collection name (and, for create, the tier limit). */
sealed interface CollectionNameResult {
    data class Ok(val name: String) : CollectionNameResult
    data object Blank : CollectionNameResult
    data object TooLong : CollectionNameResult
    data object Duplicate : CollectionNameResult
    data object LimitReached : CollectionNameResult

    /** The target is a read-only default group: rename is rejected. */
    data object ReadOnly : CollectionNameResult
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
): CollectionNameResult {
    // Only user groups count toward the limit and the duplicate check (UC-1, UC-2): [existing]
    // may be the merged list that also carries the default groups.
    val userGroups = existing.userCreated()
    return if (!canCreateUserCollection(tier, userGroups.size)) {
        CollectionNameResult.LimitReached
    } else {
        validateCollectionName(raw, userGroups)
    }
}

/** The user-created subset of a merged collection list (defaults dropped, order kept). */
internal fun List<UserCollection>.userCreated(): List<UserCollection> = filterNot { it.isDefault }

/** Localized copy suffixes: [first] is `(copy)`, [numbered] a `String.format` template `(copy %1$d)`. */
data class CopySuffixes(val first: String, val numbered: String)

/**
 * Name for a duplicate of a group called [base]: `"<base> (copy)"`, then `"(copy 2)"`, `"(copy 3)"`...
 * The first candidate whose [validateCollectionName] verdict is Ok against [existing] wins (case
 * insensitive). The base is cut by code points so the whole name fits [COLLECTION_NAME_MAX], so the
 * longer numbered suffixes shorten it further. At most `existing.size + 1` attempts are needed.
 */
internal fun copyCollectionName(base: String, existing: List<UserCollection>, suffixes: CopySuffixes): String {
    val trimmedBase = base.trim()
    var attempt = 1
    while (true) {
        val suffix = if (attempt == 1) suffixes.first else String.format(Locale.ROOT, suffixes.numbered, attempt)
        val room = (COLLECTION_NAME_MAX - suffix.codePointCount(0, suffix.length) - 1).coerceAtLeast(0)
        val cut = trimmedBase.offsetByCodePoints(0, minOf(room, trimmedBase.codePointCount(0, trimmedBase.length)))
        val candidate = "${trimmedBase.substring(0, cut).trimEnd()} $suffix".trim()
        if (validateCollectionName(candidate, existing) is CollectionNameResult.Ok) return candidate
        attempt++
    }
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
            highlightId = collection.highlightId,
            isDefault = collection.isDefault,
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

/** Resolves member ids to their affirmations in member order; ids [byId] no longer knows (orphans)
 *  drop out, same rule as the feed and the chip counts. */
internal fun <T> resolveCollectionMembers(memberIds: List<String>, byId: (String) -> T?): List<T> =
    memberIds.mapNotNull(byId)
