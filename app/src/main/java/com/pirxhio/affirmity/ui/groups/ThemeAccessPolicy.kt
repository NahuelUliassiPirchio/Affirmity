package com.pirxhio.affirmity.ui.groups

import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.access.isUnlocked
import com.pirxhio.affirmity.access.offersAdUnlock
import com.pirxhio.affirmity.access.resolveAccess

/**
 * Theme-level facade over the content-type-agnostic [resolveAccess] (mirrors
 * [groupAccessDecision]/[catalogAccessDecision] at a finer grain, "Your feed" refactor). A theme
 * has no [com.pirxhio.affirmity.access.ContentAccess] declaration of its own.
 *
 * DISCOVERY (verified against the generated catalog, not anticipated by the plan): every one of
 * the 74 generated themes mixes Free and Pro collections -- there is no theme where every
 * collection shares one tier. A [com.pirxhio.affirmity.access.mostRestrictive] fold over every
 * collection sharing a `themeId` (the literal "mirrors `catalogAccessDecision`" reading) would
 * therefore resolve EVERY theme locked for a Free user, breaking the entire default selection
 * (`defaultThematicThemeIds` would be empty and no theme could ever be toggled on by a Free user).
 * That fold is correct for [catalogAccessDecision] because there it composes TWO gates on the SAME
 * row (its group and its own collection) -- an AND. A theme spans MANY distinct collections, and
 * the right composition for "can a Free user get anything from this theme at all" is an OR: this
 * theme is toggleable the moment ANY collection under it (or the group itself, once gated) is
 * unlocked. The individually-locked rows under an otherwise-toggleable theme are still correctly
 * hidden per-row by the existing, unchanged [catalogAccessDecision] fold inside
 * `AffirmityAppState.filteredAffirmations` -- this function only ever gates the SELECTION surface
 * (is the theme itself choosable), mirroring how a universe with a locked collection is still
 * selectable today (see `GroupBadge.PARTIALLY_LOCKED` / `deriveCatalogBadge`).
 *
 * `alwaysSelected` is short-circuited via [groupAccessDecision] itself (unchanged) -- but no real
 * catalog theme ever belongs to the `personalizadas` universe (personalizadas is not a catalog
 * theme, scope decision #2), so that branch is unreachable in practice here; kept only so an
 * unknown/future theme under an `alwaysSelected` group can't be restricted by its collections.
 *
 * An unknown [themeId] (never emitted by [catalogThemesById]), an unknown parent universe, or a
 * theme with zero collections resolves [AccessDecision.Unlocked] / the group's own decision -- the
 * same "can't be MORE restrictive than nothing is known" posture [catalogAccessDecision] takes for
 * an unknown collection.
 */
fun themeAccessDecision(
    themeId: String,
    tier: AccessTier,
    grants: AdUnlockState,
    nowMillis: Long,
): AccessDecision {
    val theme = catalogThemesById()[themeId] ?: return AccessDecision.Unlocked
    val group = catalogUniverseGroups().firstOrNull { it.id == theme.universeId }
        ?: return AccessDecision.Unlocked
    val groupDecision = groupAccessDecision(group, tier, grants, nowMillis)
    if (group.alwaysSelected || !groupDecision.isUnlocked) return groupDecision

    val collections = catalogCollections().filter { it.themeId == themeId }
    if (collections.isEmpty()) return groupDecision

    val collectionDecisions = collections.map { collection ->
        resolveAccess(
            key = ContentKey(ContentType.AFFIRMATION_COLLECTION, collection.id),
            content = collection.access,
            userTier = tier,
            grants = grants,
            nowMillis = nowMillis,
        )
    }
    return collectionDecisions.firstOrNull { it.isUnlocked } ?: collectionDecisions.first()
}

fun isThemeLocked(decision: AccessDecision): Boolean = !decision.isUnlocked

fun isThemeToggleable(decision: AccessDecision): Boolean = decision.isUnlocked

/** Spec parity with [canWatchAdToUnlock] -- unused by v1 of the "Your feed" UI (the "Go deeper"
 *  section shows a PRO badge only, no ad CTA, per the mockup), kept for symmetry with the
 *  group/collection policies and so a future ad-unlockable theme tier needs no new predicate. */
fun canWatchAdForTheme(decision: AccessDecision): Boolean = decision.offersAdUnlock
