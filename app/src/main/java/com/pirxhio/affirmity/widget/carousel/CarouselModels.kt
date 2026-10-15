package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.data.local.CatalogAffirmationRef

/** Where a carousel instance draws its affirmations from (spec: "source"). */
sealed interface CarouselSource {
    data object Favorites : CarouselSource

    /** A catalog universe, or the owned "Personalizadas" group. */
    data class Theme(val groupId: String) : CarouselSource

    /** A user-created or default collection. */
    data class Collection(val collectionId: String) : CarouselSource
}

/**
 * Lightweight, text-free handle on one affirmation. [isFreeTier] is computed under
 * `AccessTier.FREE` with no grants, NEVER under the user's tier or grants, so it is
 * the only thing deciding whether a rendered text may be persisted.
 */
data class CarouselRef(
    val id: String,
    val groupId: String,
    val collectionId: String?,
    val isFreeTier: Boolean,
)

/** Why a source could not be used; drives the localized notice line. */
enum class FallbackReason { SOURCE_MISSING, SOURCE_EMPTY, SOURCE_LOCKED, DATA_UNAVAILABLE }

sealed interface PoolResult {
    val refs: List<CarouselRef>

    data class Resolved(override val refs: List<CarouselRef>) : PoolResult

    /** Free catalog pool. [reason] is `null` only when no source is configured (no notice). */
    data class Fallback(val reason: FallbackReason?, override val refs: List<CarouselRef>) : PoolResult
}

/** Owned (Personalizadas) affirmation ids, or [Unavailable] when the read failed or timed out. */
sealed interface OwnedRead {
    data class Available(val ids: List<String>) : OwnedRead
    data object Unavailable : OwnedRead
}

/** Resolved tier and ad-unlock grants. `sessionUnlocks` is always empty in the widget. */
data class CarouselAccess(val tier: AccessTier, val grants: AdUnlockState) {
    companion object {
        /** Used whenever entitlement or grants cannot be determined (fail closed). */
        val FailClosed = CarouselAccess(AccessTier.FREE, AdUnlockState())
    }
}

/**
 * Everything the pure pool builder needs, preloaded off the main thread. [catalogRefs] holds only
 * the catalog rows relevant to the source (group rows for a theme, member rows for favorites or a
 * collection); [fallbackRefs] is the whole catalog, filtered to free rows by the builder.
 */
data class CarouselSnapshot(
    val favoriteIds: List<String> = emptyList(),
    val owned: OwnedRead = OwnedRead.Available(emptyList()),
    /** Member ids per user or default collection, in collection order. */
    val collections: Map<String, List<String>> = emptyMap(),
    val catalogRefs: List<CatalogAffirmationRef> = emptyList(),
    val fallbackRefs: List<CatalogAffirmationRef> = emptyList(),
)
