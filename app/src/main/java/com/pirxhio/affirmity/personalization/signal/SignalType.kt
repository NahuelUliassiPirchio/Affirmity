package com.pirxhio.affirmity.personalization.signal

/**
 * Personalization signal types and their default weights (spec "Signal emission at existing
 * feature call sites", design D4/interfaces). Weights are starting points, not final (proposal) --
 * they are retunable via [com.pirxhio.affirmity.personalization.scoring.ScoringWeights] without a
 * migration because raw signals are stored, never precomputed scores (design D2).
 *
 * [MEDITATION_STARTED]/[MEDITATION_COMPLETED] are reserved with weight 0 and have no call sites in
 * V1 -- meditation categories have no stable id / theme mapping today (design D8).
 */
enum class SignalType(val defaultWeight: Int) {
    AFFIRMATION_SAVED(5),
    THEME_ADDED(4),
    COMPASS_ANSWERED(3),
    SURFACE_OPENED(2),
    AFFIRMATION_VIEWED(1),
    AFFIRMATION_PERSONALIZED(4),
    AFFIRMATION_SHARED(3),
    MEDITATION_STARTED(0),
    MEDITATION_COMPLETED(0),
}
