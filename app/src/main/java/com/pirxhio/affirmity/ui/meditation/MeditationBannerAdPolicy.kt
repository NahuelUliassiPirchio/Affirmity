package com.pirxhio.affirmity.ui.meditation

import com.pirxhio.affirmity.access.AccessTier

/** The ONE gate for the meditation banner. Pro pays to not see ads; every other tier sees it. */
fun shouldShowMeditationBanner(tier: AccessTier): Boolean = tier != AccessTier.PRO
