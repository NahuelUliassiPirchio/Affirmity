package com.pirxhio.affirmity.personalization.scoring

/**
 * Output of [PersonalizationScoring.profile] (design D4). Plain data -- consumed by
 * `rankedRecommendedSurfaces()` (Slice 5) and [com.pirxhio.affirmity.personalization.divergence]
 * (Slice 6).
 */
data class PersonalizationProfile(
    val themeScores: Map<String, Double>,
    val universeScores: Map<String, Double>,
    val dominantTone: String?,
    val significantSignalCount: Int,
    val isColdStart: Boolean,
)
