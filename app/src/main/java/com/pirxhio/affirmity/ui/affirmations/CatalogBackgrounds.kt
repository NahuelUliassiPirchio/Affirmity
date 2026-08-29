package com.pirxhio.affirmity.ui.affirmations

import com.pirxhio.affirmity.data.AffirmationBackground

/**
 * Backgrounds for catalog affirmations are DERIVED, never stored (design D4): the source corpus
 * has no background field at all, so a stored value would be data the import invents. A per-
 * universe 4-shade palette indexed by a stable hash of the affirmation id keeps each card
 * deterministic (the same affirmation always looks the same) and varied within a group, with zero
 * background columns in `catalog_affirmations` or `catalogAffirmations`.
 */

/** 14 universe-scoped 4-shade palettes, teal-adjacent to match the app's existing default
 *  (`0xFF00696F`), each shifted so a universe reads as its own family. Falls back to
 *  [DEFAULT_PALETTE] for any id not in this map, so a future content drop never crashes. */
private val PALETTES: Map<String, List<String>> = mapOf(
    "self_worth" to listOf("#00696F", "#12777C", "#0F5A5E", "#1A8489"),
    "mind_anxiety" to listOf("#4B5FA8", "#5A6BB5", "#3D4E8F", "#6577C2"),
    "calm_peace" to listOf("#2E8B78", "#3B9B87", "#237560", "#48A896"),
    "body_energy_wellbeing" to listOf("#B4652F", "#C0763F", "#9C5426", "#CC864F"),
    "change_loss_new_beginnings" to listOf("#6E5AA8", "#7D6BB5", "#5C4A8F", "#8A79C2"),
    "confidence_courage" to listOf("#B33F3F", "#C0524F", "#9C3232", "#CC6660"),
    "connection_belonging" to listOf("#3F8FB3", "#4F9EC0", "#32789C", "#60ADCC"),
    "expectations_boundaries_freedom" to listOf("#5F8F3F", "#6E9E4F", "#4C7832", "#7EAD60"),
    "gratitude_hope_possibility" to listOf("#B3903F", "#C09E4F", "#9C7832", "#CCAC60"),
    "love_desire_romantic_relationships" to listOf("#B33F7C", "#C04F8A", "#9C3268", "#CC6098"),
    "motivation_discipline_responsibility" to listOf("#3F5FB3", "#4F6FC0", "#32499C", "#607FCC"),
    "presence_acceptance_control" to listOf("#3FB3A0", "#4FC0AF", "#329C8B", "#60CCBC"),
    "purpose_identity_direction" to listOf("#8F3FB3", "#9E4FC0", "#78329C", "#AD60CC"),
    "work_money_growth" to listOf("#3FB35F", "#4FC06F", "#329C4C", "#60CC7F"),
)

private val DEFAULT_PALETTE = listOf("#00696F", "#12777C", "#0F5A5E", "#1A8489")

/** Pure function of `(groupId, id)` -- deterministic, never reads I/O or state (design D4). */
fun forCatalogAffirmation(groupId: String, id: String): AffirmationBackground.Color {
    val palette = PALETTES[groupId] ?: DEFAULT_PALETTE
    val index = Math.floorMod(id.hashCode(), palette.size)
    return AffirmationBackground.Color(palette[index])
}
