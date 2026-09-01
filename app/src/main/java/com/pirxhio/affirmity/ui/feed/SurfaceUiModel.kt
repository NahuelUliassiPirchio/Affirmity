package com.pirxhio.affirmity.ui.feed

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.pirxhio.affirmity.ui.groups.catalogThemes
import com.pirxhio.affirmity.ui.groups.catalogUniverseGroups

/**
 * A discovery "surface" card in [YourFeedScreen]'s grid (scope decision #5): 1:1 with an existing
 * [com.pirxhio.affirmity.ui.groups.AffirmationGroup] (universe) -- UI-only, references existing
 * theme/group ids, no new content hierarchy. [themeIds] is every distinct theme under that
 * universe; [recommendedThemeIds] is the subset shown first in [SurfaceDetailBottomSheet]'s
 * "Suggested for you" section.
 */
data class SurfaceUiModel(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val themeIds: List<String>,
    val recommendedThemeIds: List<String>,
)

/**
 * Deterministic default surface set + ranking (scope decision #5): one [SurfaceUiModel] per
 * universe, catalog `order`-derived, ready to be swapped for onboarding-informed personalization
 * later without touching any composable -- callers always take `recommendedSurfaces: List<SurfaceUiModel>`
 * as a parameter, never compute it internally.
 */
fun defaultRecommendedSurfaces(): List<SurfaceUiModel> {
    val themeIdsByUniverse = catalogThemes().groupBy(keySelector = { it.universeId }, valueTransform = { it.id })
    return catalogUniverseGroups().map { group ->
        val themeIds = themeIdsByUniverse[group.id].orEmpty()
        SurfaceUiModel(
            id = group.id,
            titleRes = group.titleRes,
            subtitleRes = group.descriptionRes,
            icon = surfaceIcons[group.id] ?: group.icon,
            themeIds = themeIds,
            recommendedThemeIds = themeIds.take(3),
        )
    }
}

/**
 * Curated per-universe icons (feedback: the generated catalog emits the same
 * [Icons.Filled.AutoAwesome] for all 14 universes -- `CatalogTaxonomy.kt` is generated and must
 * not be hand-edited, so the override lives here instead). Falls back to the generated
 * [AffirmationGroup.icon] for any universe id this map doesn't cover, so a future catalog
 * regeneration never crashes on a missing icon -- it just loses the curation until this map is
 * extended.
 */
private val surfaceIcons: Map<String, ImageVector> = mapOf(
    "self_worth" to Icons.Filled.SelfImprovement,
    "confidence_courage" to Icons.Filled.EmojiEvents,
    "calm_peace" to Icons.Filled.Spa,
    "mind_anxiety" to Icons.Filled.Psychology,
    "presence_acceptance_control" to Icons.Filled.Balance,
    "gratitude_hope_possibility" to Icons.Filled.WbSunny,
    "motivation_discipline_responsibility" to Icons.Filled.LocalFireDepartment,
    "work_money_growth" to Icons.Filled.TrendingUp,
    "body_energy_wellbeing" to Icons.Filled.FitnessCenter,
    "love_desire_romantic_relationships" to Icons.Filled.Favorite,
    "connection_belonging" to Icons.Filled.Groups,
    "change_loss_new_beginnings" to Icons.Filled.Autorenew,
    "expectations_boundaries_freedom" to Icons.Filled.OpenInFull,
    "purpose_identity_direction" to Icons.Filled.Explore,
)
