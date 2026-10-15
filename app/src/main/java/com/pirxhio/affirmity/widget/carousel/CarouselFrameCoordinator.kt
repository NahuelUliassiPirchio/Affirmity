package com.pirxhio.affirmity.widget.carousel

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Upper bound for one whole load (capture, render, store, theme). The render itself is bounded by
 * the per-read timeouts ([CAROUSEL_READ_TIMEOUT_MILLIS] for entitlement, ad unlocks and owned
 * data, which may run one after another), so 15 s only trips when something hangs outside them
 * (Room, the state store); it still leaves the session time to publish a replay or placeholder.
 */
const val CAROUSEL_LOAD_TIMEOUT_MILLIS = 15_000L

/**
 * One rendered frame and how to draw it. Lives only in memory: nothing here is persisted, so gated
 * or private text never reaches the widget's stored state.
 *
 * @property nonce the refresh nonce of the state this frame was rendered from.
 * @property context resources in the catalog locale; `null` keeps the composition's own context.
 * @property account whom [frame] belongs to (see [CarouselAccount]); `null` for a placeholder, which
 *   has no frame and is safe for anyone.
 */
internal data class CarouselView(
    val frame: CarouselFrame?,
    val theme: CarouselTheme,
    val nonce: Long,
    val context: Context? = null,
    val account: String? = null,
)

/**
 * The view a composition may draw for [currentAccount], resolved when it renders. The coordinator
 * checks the account when it publishes, but the shared view is drawn later, so a switch in between
 * must not show the previous account's frame. Returns [view] itself when it is acceptable (a
 * placeholder, or a frame of [currentAccount]); otherwise the placeholder in the default theme.
 */
internal fun viewForRender(view: CarouselView?, currentAccount: String?): CarouselView? {
    if (view == null || view.frame == null) return view
    if (currentAccount != null && view.account == currentAccount) return view
    return view.copy(frame = null, theme = CarouselTheme.Default, account = null)
}

/** How a frame is drawn: header/marker theme and the localized context for its strings. */
internal data class CarouselStyle(val theme: CarouselTheme, val context: Context? = null)

/**
 * The single loader of one widget session. Glance composes every responsive size separately, and a
 * running session never calls `provideGlance` again, so loading lives here instead of in any
 * composition: [run] is launched once in the session scope, loads strictly one frame at a time,
 * and publishes it to [views], which every size only collects.
 *
 * Each load captures the state it renders from and consumes `advancePending` in ONE store
 * transaction, so the nonce it covers and the advance it applies always belong together. A
 * refresh or advance that lands later is kept by [mergeRendered] and loaded next. The account is
 * checked once more right before publishing, with no suspension in between, so one account's
 * frame never reaches another. Nothing in here swallows cancellation.
 *
 * @param resolveAccount the account currently signed in (see [CarouselAccount]); may throw.
 * @param render loads the frame for a state, whether to advance, and the account it is for.
 * @param style resolves theme and locale for the rendered state; must not throw.
 */
internal class CarouselFrameCoordinator(
    private val store: CarouselStateStore,
    private val resolveAccount: () -> String,
    private val render: suspend (state: CarouselState, advance: Boolean, account: String) -> RenderOutcome,
    private val style: suspend (CarouselState) -> CarouselStyle,
    private val onError: (Exception) -> Unit = {},
    private val loadTimeoutMillis: Long = CAROUSEL_LOAD_TIMEOUT_MILLIS,
) {
    private val requested = MutableStateFlow(0L)
    private val published = MutableStateFlow<CarouselView?>(null)
    private val reloads = Channel<Unit>(Channel.CONFLATED)
    private val loading = Mutex()
    private var covered = Long.MIN_VALUE

    /** The latest frame; `null` only until the first load has finished. */
    val views: StateFlow<CarouselView?> = published.asStateFlow()

    /** Reports the refresh nonce a composition sees; idempotent, so every size may call it. */
    fun request(nonce: Long) {
        requested.update { maxOf(it, nonce) }
    }

    /** Loads again whatever the nonce says, e.g. when a composition found the account changed. */
    fun requestReload() {
        reloads.trySend(Unit)
    }

    suspend fun awaitFirstView(): CarouselView = views.filterNotNull().first()

    /** Loads the first frame, then one more for every newer request. Runs until cancelled. */
    suspend fun run(): Nothing {
        coroutineScope {
            // Forced and nonce-driven loads share a lock, so only one load runs at a time.
            launch { for (ignored in reloads) loadExclusively() }
            loadExclusively()
            requested.collect { nonce -> if (needsReload(nonce, covered)) loadExclusively() }
        }
        error("unreachable: a StateFlow never completes")
    }

    private suspend fun loadExclusively() = loading.withLock { load() }

    private class Loaded(val outcome: RenderOutcome, val account: String?, val style: CarouselStyle)

    private suspend fun load() {
        var snapshot: CarouselState? = null
        val loaded = try {
            withTimeoutOrNull(loadTimeoutMillis) {
                val captured = captureForLoad(store)
                snapshot = captured
                var renderedFor: String? = null
                val outcome = renderGuarded(captured, resolveAccount, onError) { account ->
                    renderedFor = account
                    render(captured, captured.advancePending, account)
                }
                // A render for an account that signed out or switched meanwhile is never stored.
                if (!outcome.stale) persist(captured, outcome)
                Loaded(outcome, renderedFor, style(outcome.state))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
            null
        }
        snapshot?.let { covered = maxOf(covered, it.refreshNonce) }
        val nonce = snapshot?.refreshNonce ?: 0L

        // From here on nothing suspends: the account checked is the one the frame is published for.
        val account = currentAccountOrNull()
        published.value = when {
            loaded == null -> fallbackView(snapshot, account, nonce)
            account == null || account != loaded.account ->
                CarouselView(null, CarouselTheme.Default, nonce, loaded.style.context)
            else -> CarouselView(loaded.outcome.frame, loaded.style.theme, nonce, loaded.style.context, account)
        }
    }

    /** Stores the render without undoing anything raised meanwhile; a newer refresh loads again. */
    private suspend fun persist(basedOn: CarouselState, outcome: RenderOutcome) {
        try {
            val current = store.update { current -> mergeRendered(current, basedOn, outcome.state) }
            request(current.refreshNonce)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed write must not hide a frame that was rendered fine. An advance consumed by
            // this load is lost with it (skipped once), the same accepted worst case as a timeout
            // or a cancelled session; see captureForLoad.
            onError(e)
        }
    }

    /** Timeout or failure: the account's last free frame if there is one, else the placeholder. */
    private fun fallbackView(snapshot: CarouselState?, account: String?, nonce: Long): CarouselView {
        val replay = account?.let { snapshot?.replayableLast(it) }
        val frame = replay?.let { CarouselFrame(it.text, it.subtitle, null, isFreeTier = true) }
        return CarouselView(frame, CarouselTheme.Default, nonce, account = if (frame != null) account else null)
    }

    private fun currentAccountOrNull(): String? = try {
        resolveAccount()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
        null
    }
}
