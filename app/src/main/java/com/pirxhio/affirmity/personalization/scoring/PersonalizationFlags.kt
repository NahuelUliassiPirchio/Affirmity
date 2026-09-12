package com.pirxhio.affirmity.personalization.scoring

/**
 * Rollout flags for personalization features. Plain Kotlin consts -- no remote-config
 * infrastructure exists in this codebase (design D11).
 */
object PersonalizationFlags {
    /** Slice 5: gates the `rankedRecommendedSurfaces()` seam swap at the `MainActivity.kt` call
     * site. Stays `false` until Slice 5 ships; while disabled the seam always falls back to
     * `defaultRecommendedSurfaces()`. */
    const val RANKED_SURFACES_ENABLED: Boolean = false
}
