package com.pirxhio.affirmity.personalization.divergence

import org.junit.Assert.assertEquals
import org.junit.Test

class DivergenceThresholdsTest {

    @Test
    fun `locked divergence thresholds remain unchanged`() {
        assertEquals(14, DivergenceThresholds.MIN_DAYS_OF_DATA)
        assertEquals(5, DivergenceThresholds.MIN_STRONG_SIGNALS)
        assertEquals(2.0, DivergenceThresholds.SCORE_RATIO_VS_WEAKEST_GOAL, 0.0)
        assertEquals(1, DivergenceThresholds.MAX_SUGGESTIONS_SHOWN)
        assertEquals(30, DivergenceThresholds.DISMISS_COOLDOWN_DAYS)
        assertEquals(1, DivergenceThresholds.MAX_PROMPTS_PER_QUARTER)
    }
}
