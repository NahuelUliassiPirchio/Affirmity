package com.pirxhio.affirmity.ui.affirmations

import com.pirxhio.affirmity.access.AccessTier

/** Minimum gap between two round-end interstitials, so a fast swiper is never spammed. */
const val ROUND_INTERSTITIAL_COOLDOWN_MS = 5 * 60 * 1000L

enum class RoundInterstitialDecision { SHOW, SKIP_PREMIUM, SKIP_COOLDOWN }

/**
 * The ONE gate for the round-end interstitial (mirrors `shouldShowMeditationBanner`). Pro pays to
 * not see ads. A [lastShownAtMillis] in the future (clock rewound) is treated as stale rather than
 * locking the user out of the cooldown logic. "Once per round" is guaranteed upstream: [RoundTracker]
 * reports each completed round exactly once.
 */
fun decideRoundInterstitial(
    tier: AccessTier,
    lastShownAtMillis: Long?,
    nowMillis: Long,
): RoundInterstitialDecision {
    if (tier == AccessTier.PRO) return RoundInterstitialDecision.SKIP_PREMIUM
    if (lastShownAtMillis != null && lastShownAtMillis <= nowMillis &&
        nowMillis - lastShownAtMillis < ROUND_INTERSTITIAL_COOLDOWN_MS
    ) {
        return RoundInterstitialDecision.SKIP_COOLDOWN
    }
    return RoundInterstitialDecision.SHOW
}

fun shouldShowRoundInterstitial(tier: AccessTier, lastShownAtMillis: Long?, nowMillis: Long): Boolean =
    decideRoundInterstitial(tier, lastShownAtMillis, nowMillis) == RoundInterstitialDecision.SHOW
