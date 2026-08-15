package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.data.repository.EntitlementTier

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
fun resolveTier(doc: RawEntitlementDoc?, nowMillis: Long): EntitlementTier {
    if (doc == null || doc.tier != "pro") return EntitlementTier.FREE
    val expiry = doc.expiryTimeMillis
    if (expiry != null && nowMillis >= expiry) return EntitlementTier.FREE
    return EntitlementTier.PRO
}

/**
 * Auto-deselect on downgrade (design.md D8, spec's "Clean deselect on downgrade with active
 * selection"): drops every id in [proOnlyIds] from [selected], then re-satisfies the
 * minimum-selection invariant by falling back to [defaultThematicIds] if that removal left no
 * thematic group selected (mirrors `AffirmityAppState.isDraftSelectionValid`'s
 * `id != PERSONALIZADAS_GROUP_ID` check). `personalizadas` is never touched -- it is never a
 * member of [proOnlyIds] in practice (`alwaysSelected` short-circuits `GroupAccessPolicy`).
 */
fun deselectLockedGroups(
    selected: Set<String>,
    proOnlyIds: Set<String>,
    defaultThematicIds: Set<String>,
): Set<String> {
    val cleaned = selected - proOnlyIds
    val hasThematicSelection = cleaned.any { it != PERSONALIZADAS_GROUP_ID }
    return if (hasThematicSelection) cleaned else cleaned + defaultThematicIds
}
