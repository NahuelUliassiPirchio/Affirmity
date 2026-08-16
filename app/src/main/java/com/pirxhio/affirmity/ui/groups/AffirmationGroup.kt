package com.pirxhio.affirmity.ui.groups

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
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

/** `access = ContentAccess.Free` here is the honest declaration -- `alwaysSelected` short-circuits
 * the entitlement check in `GroupAccessPolicy`, so this group stays permanently checked and
 * non-interactive for Free and Pro users alike, regardless of what [AffirmationGroup.access] says.
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
    access = ContentAccess.Free,
    badgeOverride = GroupBadge.PREMIUM,
    alwaysSelected = true,
    isThematic = false,
)

/** Selector order: personalizadas first (locked on), then the thematic groups. */
fun selectableAffirmationGroups(): List<AffirmationGroup> =
    listOf(PERSONALIZADAS_GROUP) + defaultAffirmationGroups()

fun defaultAffirmationGroups(): List<AffirmationGroup> = listOf(
    AffirmationGroup(
        id = "bienestar",
        titleRes = R.string.affirmation_group_bienestar_title,
        descriptionRes = R.string.affirmation_group_bienestar_description,
        icon = Icons.Filled.Favorite,
        access = ContentAccess.Free,
    ),
    AffirmationGroup(
        id = "autocuidado",
        titleRes = R.string.affirmation_group_autocuidado_title,
        descriptionRes = R.string.affirmation_group_autocuidado_description,
        icon = Icons.Filled.SelfImprovement,
        access = ContentAccess.Pro,
    ),
    AffirmationGroup(
        id = "fuerza_de_voluntad",
        titleRes = R.string.affirmation_group_fuerza_title,
        descriptionRes = R.string.affirmation_group_fuerza_description,
        icon = Icons.Filled.FitnessCenter,
        access = ContentAccess.ProOrAdPerUse,
    ),
)
