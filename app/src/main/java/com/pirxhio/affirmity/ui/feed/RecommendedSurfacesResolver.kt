package com.pirxhio.affirmity.ui.feed

import com.pirxhio.affirmity.personalization.scoring.PersonalizationProfile
import kotlinx.coroutines.CancellationException

/**
 * Crash boundary for the feed's optional personalization path. The disabled branch returns before
 * invoking [profileLoader], and every failure in loading or ranking degrades to the default feed.
 * [CancellationException] is rethrown, never swallowed as a "failure" -- structured concurrency
 * (e.g. the caller's `LaunchedEffect` leaving composition) must still cancel this coroutine.
 */
suspend fun resolveRecommendedSurfaces(
    rankedSurfacesEnabled: Boolean,
    profileLoader: suspend () -> PersonalizationProfile,
    ranker: (PersonalizationProfile) -> List<SurfaceUiModel> = ::rankedRecommendedSurfaces,
): List<SurfaceUiModel> {
    val defaults = defaultRecommendedSurfaces()
    if (!rankedSurfacesEnabled) return defaults

    return try {
        ranker(profileLoader())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        defaults
    }
}
