package com.pirxhio.affirmity.widget.carousel

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.glance.LocalContext
import androidx.glance.currentState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One rendered frame and how to draw it. Lives only in composition memory: nothing here is
 * persisted, so gated or private text never reaches the widget's stored state.
 *
 * @property nonce the refresh nonce of the state this frame was rendered from.
 * @property context resources in the catalog locale; `null` keeps the composition's own context.
 */
internal data class CarouselView(
    val frame: CarouselFrame?,
    val theme: CarouselTheme,
    val nonce: Long,
    val context: Context? = null,
)

/**
 * Shows [initial] and renders again whenever the stored refresh nonce moves past the one the shown
 * frame was rendered from. A running Glance session never calls `provideGlance` again (`update`
 * only replaces the state), so this is what makes sign-out, account switches, entitlement changes
 * and rotation visible within a live session. Reloads run one at a time and a newer request never
 * cancels one in flight (that would drop an advance it already consumed); the newer request waits
 * and reloads only if the frame it finds is still older. [reload] must not throw except for
 * cancellation. [content] draws the current view (a seam for tests).
 */
@Composable
internal fun CarouselLiveContent(
    initial: CarouselView,
    reload: suspend () -> CarouselView,
    content: @Composable (CarouselView) -> Unit = { CarouselContent(it.frame, it.theme) },
) {
    var view by remember { mutableStateOf(initial) }
    val requestedNonce = currentState(CarouselStateCodec.REFRESH_NONCE) ?: 0L
    val currentReload by rememberUpdatedState(reload)
    val loading = remember { Mutex() }
    LaunchedEffect(requestedNonce) {
        loading.withLock {
            if (needsReload(requestedNonce, view.nonce)) {
                withContext(NonCancellable) { view = currentReload() }
            }
        }
    }
    CompositionLocalProvider(LocalContext provides (view.context ?: LocalContext.current)) {
        content(view)
    }
}
