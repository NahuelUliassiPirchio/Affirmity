package com.pirxhio.affirmity.ui.feed

import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.personalization.goals.UserGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DivergenceSuggestionCardTest {

    @Test
    fun `every UserGoal has a divergence banner label`() {
        val unmapped = UserGoal.all.map { it.id }.filter { divergenceGoalLabelRes(it) == null }

        assertTrue(
            "UserGoal ids with no divergenceGoalLabelRes mapping (banner silently omits the " +
                "goal name if ever suggested): $unmapped",
            unmapped.isEmpty(),
        )
    }

    @Test
    fun `every catalog goal resolves to banner-scoped localized copy`() {
        assertEquals(R.string.divergence_goal_calm, divergenceGoalLabelRes("calm"))
        assertEquals(R.string.divergence_goal_confidence, divergenceGoalLabelRes("confidence"))
        assertEquals(R.string.divergence_goal_self_love, divergenceGoalLabelRes("self_love"))
        assertEquals(R.string.divergence_goal_motivation, divergenceGoalLabelRes("motivation"))
        assertEquals(R.string.divergence_goal_connection, divergenceGoalLabelRes("connection"))
        assertEquals(R.string.divergence_goal_change, divergenceGoalLabelRes("change"))
        assertEquals(R.string.divergence_goal_direction, divergenceGoalLabelRes("direction"))
    }

    @Test
    fun `unknown goal id does not expose an internal identifier`() {
        assertNull(divergenceGoalLabelRes("future_internal_goal"))
    }
}
