package com.pirxhio.affirmity.data

/**
 * Whether the home feed has something real to show. True as soon as any catalog or owned row
 * exists; otherwise only when both sources have emitted AND the catalog seed has settled, so a
 * fresh install (empty table while the seed is still running) is not reported as "resolved-empty"
 * prematurely, while a user with genuinely no content never hangs the marker.
 */
fun isFeedContentReady(
    hasCatalogRows: Boolean,
    hasOwnedRows: Boolean,
    catalogEmitted: Boolean,
    ownedEmitted: Boolean,
    catalogSeedSettled: Boolean,
): Boolean = hasCatalogRows || hasOwnedRows || (catalogEmitted && ownedEmitted && catalogSeedSettled)

/**
 * The TTFD condition behind `reportFullyDrawn()`: the first surface chosen by the splash gate is on
 * screen with real data. Onboarding needs nothing but the gate; home also needs the feed content
 * and the first streak emission of the session.
 */
fun isHomeFullyDrawn(
    initialContentResolved: Boolean,
    hasCompletedOnboarding: Boolean?,
    feedContentReady: Boolean,
    streakResolved: Boolean,
): Boolean = when {
    !initialContentResolved -> false
    hasCompletedOnboarding == false -> true
    else -> feedContentReady && streakResolved
}
