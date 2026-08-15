package com.pirxhio.affirmity.ui.groups

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.ui.graphics.vector.ImageVector
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID

/**
 * Gating tier shown as a badge on a group card. No paywall or ad-serving logic exists yet --
 * these are display-only placeholders for the future "pro account" / ads work.
 */
enum class AffirmationGroupAccess {
    FREE,
    PREMIUM,
    AD_SUPPORTED,
}

data class AffirmationGroup(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val access: AffirmationGroupAccess,
    /** Permanently checked and non-interactive; guarantees user-created affirmations stay visible. */
    val alwaysSelected: Boolean = false,
    /** True when this group participates in the minimum-selection invariant. */
    val isThematic: Boolean = true,
)

/** `access = PREMIUM` here is display-only (shows the Premium badge/icon) and must NOT be treated
 * as locked. `alwaysSelected` short-circuits the entitlement check in `GroupAccessPolicy`, so this
 * group stays permanently checked and non-interactive for Free and Pro users alike.
 *
 * This is a deliberate deferral, not an oversight: this group is currently in use for personal
 * testing, the production group definitions are not finalized, and no Premium test account exists
 * yet. Revisit when ALL of the following hold: (1) the real group catalog is frozen, (2) a Play
 * Console license/test account can exercise the Pro path end to end, and (3) a decision exists on
 * whether user-authored affirmations are a Pro feature at all. Until then keep this a single-line,
 * easily revertible piece of temporary state. */
val PERSONALIZADAS_GROUP = AffirmationGroup(
    id = PERSONALIZADAS_GROUP_ID,
    titleRes = R.string.affirmation_group_personalizadas_title,
    descriptionRes = R.string.affirmation_group_personalizadas_description,
    icon = Icons.Filled.Bookmark,
    access = AffirmationGroupAccess.PREMIUM,
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
        access = AffirmationGroupAccess.FREE,
    ),
    AffirmationGroup(
        id = "autocuidado",
        titleRes = R.string.affirmation_group_autocuidado_title,
        descriptionRes = R.string.affirmation_group_autocuidado_description,
        icon = Icons.Filled.SelfImprovement,
        access = AffirmationGroupAccess.PREMIUM,
    ),
    AffirmationGroup(
        id = "fuerza_de_voluntad",
        titleRes = R.string.affirmation_group_fuerza_title,
        descriptionRes = R.string.affirmation_group_fuerza_description,
        icon = Icons.Filled.FitnessCenter,
        access = AffirmationGroupAccess.AD_SUPPORTED,
    ),
)
