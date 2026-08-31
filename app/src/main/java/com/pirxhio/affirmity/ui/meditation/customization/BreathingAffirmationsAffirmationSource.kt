package com.pirxhio.affirmity.ui.meditation.customization

import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.data.repository.CatalogAffirmationRepository
import com.pirxhio.affirmity.ui.groups.defaultAffirmationGroups
import kotlinx.coroutines.flow.first

/**
 * Fetches the affirmation texts for the "Breathe & Affirm" hybrid meditation's affirmation phase.
 * This is the ONE place in the whole app that crosses from the meditation feature into the
 * (separate) affirmations feature -- see `MainActivity`'s pre-session enrichment step for why this
 * can't live inside `MeditationCatalogEntry.definition` itself (that function is synchronous;
 * fetching from Room is not). `meditation/breathingaffirmations/BreathingAffirmationsMeditationDefinition.kt`
 * never calls this or any repository directly -- it only ever receives the resolved [List]<String>.
 *
 * "adaptive" (the spec's default `affirmationUniverse`) is an honest stub, not a claim of real
 * personalization: no adaptive/personalization-selection logic exists anywhere else in this
 * codebase. It draws randomly across every default catalog universe -- all 14 are declared
 * [ContentAccess.Free] at the group level (`CatalogTaxonomy.kt`), so this never needs its own
 * entitlement check on top of the meditation entry's own access gate.
 */
suspend fun affirmationTextsForBreathingAffirmations(
    universe: String,
    count: Int,
    affirmationRepository: CatalogAffirmationRepository,
): List<String> {
    if (count <= 0) return emptyList()

    val groupIds = if (universe == "adaptive" || universe.isBlank()) {
        defaultAffirmationGroups().filter { it.access == ContentAccess.Free }.map { it.id }.toSet()
    } else {
        setOf(universe)
    }
    if (groupIds.isEmpty()) return emptyList()

    val affirmations = affirmationRepository.observeByGroupIds(groupIds).first()
    return affirmations.shuffled().take(count).map { it.text }
}
