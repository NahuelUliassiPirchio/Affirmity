package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.access.isUnlocked
import com.pirxhio.affirmity.access.offersAdUnlock
import com.pirxhio.affirmity.access.resolveAccess
import com.pirxhio.affirmity.ui.groups.GroupBadge

/**
 * Meditation-level facade over the content-type-agnostic [resolveAccess] (REQ-4.7, design §3.4),
 * mirroring `com.pirxhio.affirmity.ui.groups.GroupAccessPolicy.groupAccessDecision`. Thinner than
 * the group one: there is no `alwaysSelected` analogue for meditations, so nothing short-circuits
 * and this is a pure delegation. It exists anyway so that call sites never hand-build a
 * [ContentKey] — the key's construction lives in exactly one place per content type.
 */
fun meditationAccessDecision(
    entry: MeditationCatalogEntry,
    tier: AccessTier,
    grants: AdUnlockState,
    nowMillis: Long,
): AccessDecision = resolveAccess(
    key = meditationContentKey(entry.id),
    content = entry.access,
    userTier = tier,
    grants = grants,
    nowMillis = nowMillis,
)

/** The ONE place a meditation [ContentKey] is constructed. */
fun meditationContentKey(entryId: String): ContentKey = ContentKey(ContentType.MEDITATION, entryId)

fun isMeditationLocked(decision: AccessDecision): Boolean = !decision.isUnlocked

/** Spec 5's CTA gate, meditation half. Same predicate as `GroupAccessPolicy.canWatchAdToUnlock`. */
fun canWatchAdToUnlockMeditation(decision: AccessDecision): Boolean = decision.offersAdUnlock

/**
 * Badge rule, mirroring `GroupAccessPolicy.deriveBadge` minus its `alwaysSelected` branch — no
 * meditation entry is permanently on. Applies the "hide the badge once resolved-unlocked" rule
 * unconditionally (no `alwaysSelected` carve-out exists here, unlike groups).
 *
 * Branches on the resolved [AccessDecision], not the entry's static [ContentAccess] declaration:
 * a spent ONE_TIME_TRIAL resolves to [AccessDecision.LockedNeedsPro] (REQ-4.8 — a spent trial must
 * never re-offer the ad), so it must show [GroupBadge.PREMIUM], not [GroupBadge.AD_UNLOCK]. Keying
 * off `entry.access.adUnlock` instead would show the ad badge for content that can never be
 * ad-unlocked again, since the static declaration doesn't change once the trial is spent. `_entry`
 * is unused by this implementation but kept in the signature — no production call sites yet
 * (PR1), and later PRs' call-site design already assumes this shape.
 */
fun deriveMeditationBadge(_entry: MeditationCatalogEntry, decision: AccessDecision): GroupBadge? =
    when (decision) {
        is AccessDecision.Unlocked, is AccessDecision.UnlockedByAd -> null
        AccessDecision.LockedNeedsPro -> GroupBadge.PREMIUM
        is AccessDecision.LockedAdUnlockable -> GroupBadge.AD_UNLOCK
    }
