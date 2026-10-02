package com.pirxhio.affirmity.ui.affirmations

import com.pirxhio.affirmity.access.AccessTier

enum class RoundInterstitialDecision { SHOW, SKIP_PREMIUM }

/**
 * The ONE gate for the round-end interstitial (mirrors `shouldShowMeditationBanner`). Pro pays to
 * not see ads; everyone else sees one at every round end. "Once per round" is guaranteed upstream:
 * [RoundTracker] reports each completed round exactly once.
 */
fun decideRoundInterstitial(tier: AccessTier): RoundInterstitialDecision =
    if (tier == AccessTier.PRO) RoundInterstitialDecision.SKIP_PREMIUM else RoundInterstitialDecision.SHOW

fun shouldShowRoundInterstitial(tier: AccessTier): Boolean =
    decideRoundInterstitial(tier) == RoundInterstitialDecision.SHOW
