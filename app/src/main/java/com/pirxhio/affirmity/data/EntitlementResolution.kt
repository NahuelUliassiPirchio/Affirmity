package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.meditation.SessionEndReason

/**
 * Pure, Firestore-agnostic mirror of the fields `FirestoreEntitlementRepository` reads off the
 * `users/{uid}/entitlements/current` snapshot (design.md D4). Kept separate from the Cloud
 * Functions' `EntitlementDoc` (functions/src/billing.ts) since the client only ever needs to read
 * a subset, and this type has no Firestore dependency so it stays unit-testable.
 */
data class RawEntitlementDoc(
    val tier: String,
    val expiryTimeMillis: Long?,
)

/**
 * Resolves the effective client-side tier. A `null` doc (never purchased, or Firestore's offline
 * cache has nothing cached yet) is Free. A stored "pro" tier downgrades to Free once
 * [RawEntitlementDoc.expiryTimeMillis] has actually passed -- this is what lets the client honor
 * Play's grace/cancel-but-not-yet-expired states without needing its own copy of that state
 * machine (design.md D1/D5): the server already only ever writes `tier = "pro"` for a state that
 * still grants access, and this function's only job is the time-based boundary within that grant.
 */
fun resolveTier(doc: RawEntitlementDoc?, nowMillis: Long): AccessTier {
    if (doc == null || doc.tier != "pro") return AccessTier.FREE
    val expiry = doc.expiryTimeMillis
    if (expiry != null && nowMillis >= expiry) return AccessTier.FREE
    return AccessTier.PRO
}

/**
 * Auto-deselect on downgrade (design.md D8, spec's "Clean deselect on downgrade with active
 * selection"): drops every id in [proOnlyIds] from [selected] -- except one still covered by a
 * durable ad grant ([adUnlockedIds], design §10 Q4(ii): a ONE_TIME_TRIAL grant legitimately
 * survives a downgrade; PER_USE never reaches here since it is never persisted) -- then
 * re-satisfies the minimum-selection invariant by falling back to [defaultThematicIds] if that
 * removal left no thematic group selected (mirrors `AffirmityAppState.isDraftSelectionValid`'s
 * `id != PERSONALIZADAS_GROUP_ID` check). `personalizadas` is never touched -- it is never a
 * member of [proOnlyIds] in practice (`alwaysSelected` short-circuits `GroupAccessPolicy`).
 *
 * [adUnlockedIds] defaults to empty, which preserves today's exact behavior
 * (`proOnlyIds - emptySet() == proOnlyIds`).
 */
fun deselectLockedGroups(
    selected: Set<String>,
    proOnlyIds: Set<String>,
    defaultThematicIds: Set<String>,
    adUnlockedIds: Set<String> = emptySet(),
): Set<String> {
    val cleaned = selected - (proOnlyIds - adUnlockedIds)
    val hasThematicSelection = cleaned.any { it != PERSONALIZADAS_GROUP_ID }
    return if (hasThematicSelection) cleaned else cleaned + defaultThematicIds
}

/**
 * Theme-level equivalent of [deselectLockedGroups] ("Your feed" refactor): drops every id in
 * [proOnlyIds] from [selected] -- except one still covered by a durable ad grant
 * ([adUnlockedIds]) -- then falls back to [defaultThemeIds] if that removal left the selection
 * empty. Simpler than its group-level counterpart because no theme is ever `personalizadas`
 * (scope decision #2): there is no always-selected id to exempt from the emptiness check.
 */
fun deselectLockedThemes(
    selected: Set<String>,
    proOnlyIds: Set<String>,
    defaultThemeIds: Set<String>,
    adUnlockedIds: Set<String> = emptySet(),
): Set<String> {
    val cleaned = selected - (proOnlyIds - adUnlockedIds)
    return cleaned.ifEmpty { defaultThemeIds }
}

/**
 * A [AdUnlockPolicy.PER_USE][com.pirxhio.affirmity.access.AdUnlockPolicy.PER_USE] unlock on an
 * affirmation group is spent the moment its group leaves the committed selection (design §0's
 * "until the user changes their selection again" binding, design §4b). Other content types are
 * untouched -- a meditation's PER_USE unlock is bound to playback, not to this set.
 */
fun retainSelectionScopedUnlocks(
    unlocks: Set<ContentKey>,
    committedGroupIds: Set<String>,
): Set<ContentKey> = unlocks.filterNot {
    it.type == ContentType.AFFIRMATION_GROUP && it.id !in committedGroupIds
}.toSet()

/**
 * A meditation [AdUnlockPolicy.PER_USE][com.pirxhio.affirmity.access.AdUnlockPolicy.PER_USE]
 * unlock is spent the moment its playback session reaches a terminal state -- [SessionEndReason.Completed]
 * or [SessionEndReason.Cancelled] (design §5.5, REQ-5.5). Both terminal reasons spend the grant: an
 * ad watched and then abandoned mid-session is still a use. Deliberately excludes disposal from
 * `Idle` (never started) -- that carve-out is enforced by the caller (`GuidedMeditationScreen`'s
 * `requestExit` guard), not by this function. Exhaustive `when`, no `else`: if a third
 * [SessionEndReason] is ever added, this must force a decision, not default silently. Non-meditation
 * keys pass through untouched -- a safe no-op for content whose unlock is scoped some other way
 * (e.g. [retainSelectionScopedUnlocks] for affirmation groups).
 */
fun consumePlaybackScopedUnlock(
    unlocks: Set<ContentKey>,
    key: ContentKey,
    reason: SessionEndReason,
): Set<ContentKey> = when (reason) {
    SessionEndReason.Completed, SessionEndReason.Cancelled ->
        if (key.type == ContentType.MEDITATION) unlocks - key else unlocks
}
