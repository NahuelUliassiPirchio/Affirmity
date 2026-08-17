package com.pirxhio.affirmity.ui.myaffirmations

import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.access.isUnlocked
import com.pirxhio.affirmity.access.resolveAccess

/**
 * Creation-time gate for user-authored affirmations (REQ-4.5-4.7, design §3.4). Free quota is ZERO
 * by product decision (spec §0), so this is count-independent: existing affirmations are never
 * inspected (grandfathering). Delegates to [resolveAccess] rather than testing the tier directly,
 * so creation joins the same single decision point as groups (Spec 1) and meditations (Spec 3).
 * Thinnest of the three facades: no per-entry lookup (unlike meditations) and no `alwaysSelected`
 * short-circuit (unlike groups).
 */

/** The ONE place this [ContentKey] is constructed (REQ-4.6). Stable — never renamed once shipped. */
fun customAffirmationContentKey(): ContentKey = ContentKey(ContentType.CUSTOM_AFFIRMATION_SLOT, "create")

/** [grants] is passed only for signature uniformity with the other two facades — [ContentAccess.Pro]
 *  has `adUnlock = AdUnlockPolicy.NONE`, so [resolveAccess] provably never reads it here (REQ-4.7). */
fun customAffirmationAccessDecision(
    tier: AccessTier,
    grants: AdUnlockState,
    nowMillis: Long,
): AccessDecision = resolveAccess(
    key = customAffirmationContentKey(),
    content = ContentAccess.Pro,
    userTier = tier,
    grants = grants,
    nowMillis = nowMillis,
)

fun isCustomAffirmationCreationLocked(decision: AccessDecision): Boolean = !decision.isUnlocked
