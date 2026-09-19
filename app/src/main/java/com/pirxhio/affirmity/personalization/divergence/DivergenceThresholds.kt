package com.pirxhio.affirmity.personalization.divergence

/** Locked D10 thresholds for conservative, user-controlled goal suggestions. */
object DivergenceThresholds {
    const val MIN_DAYS_OF_DATA = 14
    const val MIN_STRONG_SIGNALS = 5
    const val SCORE_RATIO_VS_WEAKEST_GOAL = 2.0
    const val MAX_SUGGESTIONS_SHOWN = 1
    const val DISMISS_COOLDOWN_DAYS = 30
    const val MAX_PROMPTS_PER_QUARTER = 1
}
