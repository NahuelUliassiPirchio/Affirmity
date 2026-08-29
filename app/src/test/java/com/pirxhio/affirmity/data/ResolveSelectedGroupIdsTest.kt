package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.ui.groups.defaultAffirmationGroups
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the pure selection-resolution logic extracted from `AffirmityAppState`'s init collector
 * (design §4/§6, D17/D18): first-launch default, unknown-id filtering, personalizadas's presence
 * in every FALLBACK default (not force-included into an otherwise-healthy selection -- TEMPORARY
 * dogfooding relaxation, see the function's KDoc), and the tier-independent thematic-emptiness
 * fallback (D18's Pro-tier bug fix). */
class ResolveSelectedGroupIdsTest {

    // Post-D17: 14 curated-catalog universes + personalizadas. `self_worth` stands in as "a
    // healthy, still-known thematic id" throughout -- any real universe id works equally well.
    private val universeIds = setOf(
        "self_worth", "confidence_courage", "calm_peace", "mind_anxiety",
        "presence_acceptance_control", "gratitude_hope_possibility",
        "motivation_discipline_responsibility", "work_money_growth", "body_energy_wellbeing",
        "love_desire_romantic_relationships", "connection_belonging", "change_loss_new_beginnings",
        "expectations_boundaries_freedom", "purpose_identity_direction",
    )
    private val knownIds = universeIds + "personalizadas"
    private val defaultThematicIds = universeIds

    @Test
    fun `first-ever launch with no persisted selection falls back to all 14 default thematic ids`() {
        val resolved = resolveSelectedGroupIds(
            persisted = null,
            knownIds = knownIds,
            defaultThematicIds = defaultThematicIds,
        )

        assertEquals(universeIds + "personalizadas", resolved)
    }

    @Test
    fun `a persisted selection referencing only deleted legacy ids falls back to the 14 -- the Pro-tier bug case (D18)`() {
        // The exact scenario D18 fixes: previously this recovery lived only inside
        // EntitlementResolution.deselectLockedGroups, which is guarded by
        // `if (entitlement.tier == AccessTier.FREE)` -- so a PRO user whose persisted selection was
        // `{"personalizadas", "bienestar"}` (both legacy ids the group removal deleted) never ran
        // that recovery and landed on a personalizadas-only, thematically empty feed. This resolver
        // is tier-independent, so the same fallback fires regardless of tier.
        val resolved = resolveSelectedGroupIds(
            persisted = setOf("personalizadas", "bienestar"),
            knownIds = knownIds,
            defaultThematicIds = defaultThematicIds,
        )

        assertEquals(universeIds + "personalizadas", resolved)
    }

    @Test
    fun `a healthy persisted selection with a surviving thematic id is returned verbatim`() {
        val resolved = resolveSelectedGroupIds(
            persisted = setOf("personalizadas", "self_worth"),
            knownIds = knownIds,
            defaultThematicIds = defaultThematicIds,
        )

        assertEquals(setOf("personalizadas", "self_worth"), resolved)
    }

    @Test
    fun `an explicitly empty persisted selection also triggers the fallback (D18 -- was previously respected as empty)`() {
        // Deliberate behavior change from the pre-D18 resolver: a persisted thematic-empty set is
        // unreachable through the UI because a genuinely empty draft is invalid. A
        // personalizadas-only draft can still be valid when custom affirmations exist, but that
        // is distinct from persisting an empty set. Falling back here makes the fresh-install path
        // and the stale/empty-selection path the SAME code path (design D18).
        val resolved = resolveSelectedGroupIds(
            persisted = emptySet(),
            knownIds = knownIds,
            defaultThematicIds = defaultThematicIds,
        )

        assertEquals(universeIds + "personalizadas", resolved)
    }

    @Test
    fun `an empty draft cannot be applied even when custom affirmations exist`() {
        val valid = isDraftSelectionValid(
            draftGroupIds = emptySet(),
            hasPersonalAffirmations = true,
        )

        assertEquals(false, valid)
    }

    @Test
    fun `a deliberate personalizadas-only persisted selection is preserved verbatim, not reset to defaults`() {
        // Distinct from the legacy-ids case above: here persisted was ALREADY exactly
        // {personalizadas} -- nothing got dropped by the unknown-id filter. isDraftSelectionValid
        // now allows committing this state when the user has custom affirmations, so it must
        // survive a restart instead of snapping back to the 14 default thematic ids.
        val resolved = resolveSelectedGroupIds(
            persisted = setOf("personalizadas"),
            knownIds = knownIds,
            defaultThematicIds = defaultThematicIds,
        )

        assertEquals(setOf("personalizadas"), resolved)
    }

    @Test
    fun `a persisted selection with one unknown id among otherwise-healthy ids drops only that id`() {
        val resolved = resolveSelectedGroupIds(
            persisted = setOf("self_worth", "some_removed_group"),
            knownIds = knownIds,
            defaultThematicIds = defaultThematicIds,
        )

        assertEquals(setOf("self_worth"), resolved)
    }

    @Test
    fun `a healthy persisted selection without personalizadas is respected verbatim (TEMPORARY dogfooding relaxation)`() {
        // Was "personalizadas is force-included even if absent" before this change. Reversed
        // deliberately: this is what actually lets a user uncheck personalizadas in the selector
        // and have it stay unchecked across a restart, instead of snapping back on the next
        // observeSelectedGroupIds emission. See resolveSelectedGroupIds's KDoc to revert.
        val resolved = resolveSelectedGroupIds(
            persisted = setOf("self_worth"),
            knownIds = knownIds,
            defaultThematicIds = defaultThematicIds,
        )

        assertEquals(setOf("self_worth"), resolved)
    }

    // --- Wiring assertion (design D18): the production default is a DECLARATION, not a
    // coincidence -- `defaultAffirmationGroups()` (post-D17, the 14 catalog universes) must
    // resolve to exactly 14 thematic, Free-tier ids. -------------------------------------------

    @Test
    fun `production wiring -- all 14 default groups are thematic and Free-tier`() {
        val wired = defaultAffirmationGroups()
            .filter { it.isThematic && it.access.requiredTier == AccessTier.FREE }
        assertEquals(14, wired.size)
    }
}
