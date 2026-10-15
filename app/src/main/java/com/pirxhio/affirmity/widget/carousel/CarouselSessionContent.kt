package com.pirxhio.affirmity.widget.carousel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.LocalContext
import androidx.glance.currentState
import kotlinx.coroutines.flow.StateFlow

/**
 * What every responsive size of a running session draws: the frame the session's
 * [CarouselFrameCoordinator] published, never loading anything itself. A running session never
 * calls `provideGlance` again (`update` only replaces `LocalState`), so the composition reports the
 * refresh nonce it sees through [onRefreshRequested]; the coordinator dedupes the reports of all
 * sizes and loads once. A `null` view (never expected, the first load is awaited) shows the
 * placeholder rather than nothing.
 */
@Composable
internal fun CarouselSessionContent(views: StateFlow<CarouselView?>, onRefreshRequested: (Long) -> Unit) {
    val requestedNonce = currentState(CarouselStateCodec.REFRESH_NONCE) ?: 0L
    LaunchedEffect(requestedNonce) { onRefreshRequested(requestedNonce) }
    val view by views.collectAsState()
    CompositionLocalProvider(LocalContext provides (view?.context ?: LocalContext.current)) {
        CarouselContent(view?.frame, view?.theme ?: CarouselTheme.Default)
    }
}
