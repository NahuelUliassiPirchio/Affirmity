package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.FeedSources

// SplitMix64 finalizer constants (see https://prng.di.unimi.it/splitmix64.c). Declared as `val`
// rather than `const val` because a hex literal past Long.MAX_VALUE must be parsed as ULong and
// converted -- Kotlin const requires a compile-time Long literal, which these bit patterns aren't.
private val MIX64_C1 = 0xbf58476d1ce4e5b9UL.toLong()
private val MIX64_C2 = 0x94d049bb133111ebUL.toLong()

/**
 * Deterministic per-row sort key for the "Random order" feed toggle (design "Ordering algorithm"
 * decision). A row's position depends only on ([seed], [id]) -- never on the row's position in the
 * input list -- so adding, removing, or reordering other rows (a new favorite, a hidden
 * affirmation, an entitlement change) never disturbs the relative order of the rows that stay.
 *
 * Implemented as a SplitMix64 finalizer (mix64) over `seed xor id.hashCode()`: cheap, well-mixed,
 * and avoids `Random(seed).nextX()` per row, which would require seeking a fresh `Random` instance
 * per id to stay position-independent.
 */
internal fun feedOrderKey(seed: Long, id: String): Long {
    var z = seed xor id.hashCode().toLong()
    z = (z xor (z ushr 30)) * MIX64_C1
    z = (z xor (z ushr 27)) * MIX64_C2
    return z xor (z ushr 31)
}

/**
 * Applies [sources]'s committed random-order toggle to [items] as the FINAL ordering step (design
 * "Where ordering happens" decision). When [FeedSources.randomizeOrder] is off, [items] is returned
 * unchanged -- today's fixed own -> favorites -> catalog order, produced by whatever order the
 * caller concatenated segments in. When on, every item is re-sorted by [feedOrderKey], with [idOf]
 * as a secondary tie-break so the sort is fully deterministic even if two ids ever produced the
 * same key.
 */
internal fun <T> orderFeed(items: List<T>, sources: FeedSources, idOf: (T) -> String): List<T> =
    if (!sources.randomizeOrder) {
        items
    } else {
        items.sortedWith(compareBy<T> { feedOrderKey(sources.orderSeed, idOf(it)) }.thenBy(idOf))
    }

/**
 * Extended "Actualizar mi feed" dirty check (spec "Extended isDirty Detection"): true when the
 * draft theme selection differs from the committed one, OR when any of [FeedSources.includeFavorites],
 * [FeedSources.includeOwn], [FeedSources.randomizeOrder] differs between [draft] and [committed].
 *
 * [FeedSources.orderSeed] is deliberately EXCLUDED from the comparison (design "Dirty check"
 * decision) -- the draft always carries the last-committed seed forward (see
 * `AffirmityAppState.resetThemeDraftToCommitted`), so comparing it would make the button dirty for
 * a value the user never touched.
 */
internal fun isFeedDraftDirty(
    draftThemes: Set<String>,
    committedThemes: Set<String>?,
    draft: FeedSources,
    committed: FeedSources,
): Boolean =
    draftThemes != (committedThemes ?: emptySet<String>()) ||
        draft.includeFavorites != committed.includeFavorites ||
        draft.includeOwn != committed.includeOwn ||
        draft.randomizeOrder != committed.randomizeOrder
