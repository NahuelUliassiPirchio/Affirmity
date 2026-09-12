package com.pirxhio.affirmity.personalization.goals

import com.pirxhio.affirmity.ui.groups.catalogThemes
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCatalogTest {

    @Test
    fun `every mapped theme id still exists in the generated catalog`() {
        val realThemeIds = catalogThemes().map { it.id }.toSet()
        val missingThemeIds = GoalCatalog.allMappedThemeIds - realThemeIds

        assertTrue(
            "GoalCatalog contains theme ids removed from the generated catalog: $missingThemeIds",
            missingThemeIds.isEmpty(),
        )
    }

    @Test
    fun `every UserGoal has a GoalCatalog entry`() {
        val goalIds = UserGoal.all.map { it.id }.toSet()
        val unmapped = goalIds - GoalCatalog.themeIdsByGoalId.keys

        assertTrue(
            "UserGoal ids with no GoalCatalog mapping (score silently stays zero forever): $unmapped",
            unmapped.isEmpty(),
        )
    }

    @Test
    fun `GoalCatalog has no entries for goals that no longer exist`() {
        val goalIds = UserGoal.all.map { it.id }.toSet()
        val orphaned = GoalCatalog.themeIdsByGoalId.keys - goalIds

        assertTrue(
            "GoalCatalog maps ids that aren't in UserGoal.all (dead entries): $orphaned",
            orphaned.isEmpty(),
        )
    }
}
