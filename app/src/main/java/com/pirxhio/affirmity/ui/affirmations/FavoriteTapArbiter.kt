package com.pirxhio.affirmity.ui.affirmations

sealed interface TokenTapDecision {
    data class Wait(val key: String, val afterMillis: Long) : TokenTapDecision

    data class StartEditing(val key: String) : TokenTapDecision

    data object ToggleFavorite : TokenTapDecision
}

enum class FavoriteGesture {
    DOUBLE_TAP,
}

class FavoriteTapArbiter(
    private val doubleTapWindowMillis: Long = DEFAULT_DOUBLE_TAP_WINDOW_MILLIS,
) {
    private var lastTapKey: String? = null
    private var lastTapMillis: Long = 0L

    fun onTokenTap(key: String, nowMillis: Long): TokenTapDecision {
        if (doubleTapWindowMillis == 0L) {
            return TokenTapDecision.StartEditing(key)
        }

        val elapsedMillis = nowMillis - lastTapMillis
        val isDoubleTap =
            lastTapKey == key && elapsedMillis >= 0L && elapsedMillis < doubleTapWindowMillis
        if (isDoubleTap) {
            reset()
            return TokenTapDecision.ToggleFavorite
        }

        lastTapKey = key
        lastTapMillis = nowMillis
        return TokenTapDecision.Wait(key, doubleTapWindowMillis)
    }

    fun reset() {
        lastTapKey = null
        lastTapMillis = 0L
    }

    companion object {
        const val DEFAULT_DOUBLE_TAP_WINDOW_MILLIS = 300L
    }
}

/**
 * Bridges pointer-down timing to the token's existing click callback. Compose invokes a
 * `LinkAnnotation.Clickable` listener on pointer-up, but double-tap
 * eligibility is defined by the interval between pointer downs. Keeping that timing here lets the
 * click callback remain the only place that resolves [TokenTapDecision].
 */
class FavoriteTokenTapCoordinator(
    private val favoriteTapEnabled: Boolean = true,
    private val doubleTapWindowMillis: Long = FavoriteTapArbiter.DEFAULT_DOUBLE_TAP_WINDOW_MILLIS,
) {
    private val arbiter = FavoriteTapArbiter(doubleTapWindowMillis)
    private var latestDown: TokenDown? = null
    private var pendingClick: TokenDown? = null

    /** Returns true when this down makes a pending single-tap edit ambiguous and it must cancel. */
    fun onPointerDown(key: String, nowMillis: Long): Boolean {
        if (!favoriteTapEnabled) return false
        val down = TokenDown(key, nowMillis)
        latestDown = down
        val pending = pendingClick ?: return false
        val elapsedMillis = nowMillis - pending.nowMillis
        return elapsedMillis >= 0L && elapsedMillis < doubleTapWindowMillis
    }

    fun onTokenClick(key: String, callbackAtMillis: Long): TokenTapDecision {
        if (!favoriteTapEnabled) return TokenTapDecision.StartEditing(key)
        val downMillis = latestDown
            ?.takeIf { it.key == key }
            ?.nowMillis
            ?: callbackAtMillis
        latestDown = null
        return arbiter.onTokenTap(key, downMillis).also { decision ->
            pendingClick = when (decision) {
                is TokenTapDecision.Wait -> TokenDown(decision.key, downMillis)
                is TokenTapDecision.StartEditing,
                TokenTapDecision.ToggleFavorite -> null
            }
        }
    }

    fun reset() {
        latestDown = null
        pendingClick = null
        arbiter.reset()
    }

    private data class TokenDown(val key: String, val nowMillis: Long)
}
