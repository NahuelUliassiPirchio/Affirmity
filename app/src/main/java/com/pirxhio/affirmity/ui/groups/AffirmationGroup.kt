package com.pirxhio.affirmity.ui.groups

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.ui.graphics.vector.ImageVector
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID

/**
 * Display-only badge shown on a group card. Deliberately a separate concept from [ContentAccess]:
 * a purely decorative badge must never be able to re-enter the access decision — the exact
 * FREE/PREMIUM/AD_SUPPORTED coupling this refactor removed (design §7).
 */
enum class GroupBadge { PREMIUM, AD_UNLOCK }

data class AffirmationGroup(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val access: ContentAccess,
    /** Explicit badge override, independent of [access] (spec §0 decision-1). `null` means "derive
     * the badge from [access]'s declaration" -- see `GroupAccessPolicy.deriveBadge`. */
    val badgeOverride: GroupBadge? = null,
    /** Permanently checked and non-interactive; guarantees user-created affirmations stay visible. */
    val alwaysSelected: Boolean = false,
    /** True when this group participates in the minimum-selection invariant. */
    val isThematic: Boolean = true,
)

/** `access = ContentAccess.Pro` is the honest declaration (Spec 1 Q5, closed): custom affirmations
 * are a Pro feature -- creating one requires Pro (Spec 4's create-gate), so the group that holds
 * them is declared Pro-only too. `alwaysSelected` still short-circuits the entitlement check in
 * `GroupAccessPolicy`, so this group stays permanently checked and non-interactive for Free and Pro
 * users alike, regardless of what [AffirmationGroup.access] says -- a Free user's *existing* custom
 * affirmations (grandfathered per Spec 4) must remain visible in their daily selection even though
 * they can't add more. `access` here is provenance/labeling, not a live gate.
 *
 * `badgeOverride = GroupBadge.PREMIUM` is a deliberate, unconditional product decision (spec §0
 * decision-1): this group always shows the Premium badge, at every tier, because
 * `alwaysSelected` makes it exempt from decision-2's "hide when unlocked" rule (spec §0's
 * reconciliation formula in `GroupAccessPolicy.deriveBadge`) -- otherwise the badge would be
 * unconditionally hidden (since this group always resolves `Unlocked`), silently defeating
 * decision-1.
 */
val PERSONALIZADAS_GROUP = AffirmationGroup(
    id = PERSONALIZADAS_GROUP_ID,
    titleRes = R.string.affirmation_group_personalizadas_title,
    descriptionRes = R.string.affirmation_group_personalizadas_description,
    icon = Icons.Filled.Bookmark,
    access = ContentAccess.Pro,
    badgeOverride = GroupBadge.PREMIUM,
    alwaysSelected = true,
    isThematic = false,
)

/** Selector order: personalizadas first (locked on), then the thematic groups. */
fun selectableAffirmationGroups(): List<AffirmationGroup> =
    listOf(PERSONALIZADAS_GROUP) + defaultAffirmationGroups()

/** The 14 curated-catalog universes (design D17) -- the 3 legacy placeholder groups
 * (`bienestar`/`autocuidado`/`fuerza_de_voluntad`) are DELETED outright, no alias/fallback,
 * per the explicit user decision recorded in design D17: they held no real content and no
 * live user data referenced them. */
fun defaultAffirmationGroups(): List<AffirmationGroup> = catalogUniverseGroups()
