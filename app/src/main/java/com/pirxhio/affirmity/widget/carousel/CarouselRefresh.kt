package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockRecord
import com.pirxhio.affirmity.access.ContentKey
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/** Debounce applied before a refresh so bursts of changes cost one widget update. */
const val CAROUSEL_REFRESH_DEBOUNCE_MS = 500L

/**
 * Everything that can change which affirmations the carousel may show: the tier, the
 * durable and timed ad-unlock grants, and the favorites. Tracker state is deliberately absent, and
 * so are PER_USE session unlocks, which never count in the widget.
 */
data class CarouselRefreshKey(
    val tier: AccessTier,
    val durable: Map<ContentKey, AdUnlockRecord>,
    val timed: Map<ContentKey, AdUnlockRecord>,
    val favoriteIds: List<String>,
    /** Signed-in uid, `null` when signed out: sign-in and sign-out must refresh (owned text). */
    val uid: String? = null,
)

/** Emits only when the key actually changed, and only after the changes settle. */
@OptIn(FlowPreview::class)
fun Flow<CarouselRefreshKey>.carouselRefreshTriggers(): Flow<CarouselRefreshKey> =
    distinctUntilChanged().debounce(CAROUSEL_REFRESH_DEBOUNCE_MS)
