package com.pirxhio.affirmity.widget.carousel

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** What a rendered frame shows. [notice] is non-null iff the free fallback is active. */
data class CarouselFrame(
    val text: String,
    val subtitle: String?,
    val notice: FallbackReason?,
    val isFreeTier: Boolean,
)

/**
 * Identity of whoever the stored last frame belongs to. Owned text is private to an account, so a
 * frame may only be replayed for the account that was showing it when it was stored.
 */
object CarouselAccount {
    const val SIGNED_OUT = "signed-out"

    fun of(uid: String?): String = if (uid == null) SIGNED_OUT else "uid:$uid"
}

/** Text that is safe to persist and replay: only ever taken from a free-tier frame. */
data class LastShown(val text: String, val subtitle: String?)

/**
 * Null for any frame that is not free for everyone, so gated text can never be replayed from
 * prefs after a Pro lapse or an expired ad-unlock.
 */
fun persistedLast(frame: CarouselFrame): LastShown? =
    if (frame.isFreeTier) LastShown(frame.text, frame.subtitle) else null

/** Per-instance state, mirrored 1:1 by [CarouselStateCodec]. */
data class CarouselState(
    val source: CarouselSource? = null,
    val order: List<String> = emptyList(),
    val cursor: Int = 0,
    val advancePending: Boolean = false,
    val fallback: Boolean = false,
    val lastText: String? = null,
    val lastSubtitle: String? = null,
    val lastFree: Boolean = false,
    /** Whose frame [lastText] is (see [CarouselAccount]); null when nothing is stored. */
    val lastAccount: String? = null,
) {
    val queue: QueueState get() = QueueState(order, cursor)

    /**
     * Records [frame] as the last shown one for [account]; clears stored text unless the frame is
     * free.
     */
    fun withShown(frame: CarouselFrame, account: String): CarouselState {
        val last = persistedLast(frame)
        return copy(
            lastText = last?.text,
            lastSubtitle = last?.subtitle,
            lastFree = last != null,
            lastAccount = account.takeIf { last != null },
        )
    }

    /**
     * The stored text, only when it was marked free, text exists, and it was stored for [account].
     * A frame without an account marker (older state) never replays.
     */
    fun replayableLast(account: String): LastShown? =
        if (lastFree && lastText != null && lastAccount == account) LastShown(lastText, lastSubtitle) else null

    /** Drops the stored frame when it belongs to someone other than [account]. */
    fun forAccount(account: String?): CarouselState =
        if (account != null && lastAccount == account) this else withoutLast()

    fun withoutLast(): CarouselState =
        copy(lastText = null, lastSubtitle = null, lastFree = false, lastAccount = null)
}

/** Maps [CarouselState] to and from Glance's per-widget [Preferences] store. */
object CarouselStateCodec {
    val SOURCE = stringPreferencesKey("source")
    val ORDER = stringPreferencesKey("order")
    val CURSOR = intPreferencesKey("cursor")
    val ADVANCE_PENDING = booleanPreferencesKey("advancePending")
    val FALLBACK = booleanPreferencesKey("fallback")
    val LAST_TEXT = stringPreferencesKey("lastText")
    val LAST_SUBTITLE = stringPreferencesKey("lastSubtitle")
    val LAST_FREE = booleanPreferencesKey("lastFree")
    val LAST_ACCOUNT = stringPreferencesKey("lastAccount")

    private const val ORDER_SEPARATOR = "\n"

    fun read(prefs: Preferences): CarouselState = CarouselState(
        source = prefs[SOURCE]?.let(::decodeSource),
        order = prefs[ORDER]?.takeIf { it.isNotEmpty() }?.split(ORDER_SEPARATOR).orEmpty(),
        cursor = prefs[CURSOR] ?: 0,
        advancePending = prefs[ADVANCE_PENDING] ?: false,
        fallback = prefs[FALLBACK] ?: false,
        lastText = prefs[LAST_TEXT],
        lastSubtitle = prefs[LAST_SUBTITLE],
        lastFree = prefs[LAST_FREE] ?: false,
        lastAccount = prefs[LAST_ACCOUNT],
    )

    fun write(prefs: MutablePreferences, state: CarouselState) {
        state.source?.let { prefs[SOURCE] = encodeSource(it) } ?: prefs.remove(SOURCE)
        val order = state.order.take(MAX_ORDER)
        prefs[ORDER] = order.joinToString(ORDER_SEPARATOR)
        prefs[CURSOR] = state.cursor.coerceIn(0, maxOf(order.size - 1, 0))
        prefs[ADVANCE_PENDING] = state.advancePending
        prefs[FALLBACK] = state.fallback
        state.lastText?.let { prefs[LAST_TEXT] = it } ?: prefs.remove(LAST_TEXT)
        state.lastSubtitle?.let { prefs[LAST_SUBTITLE] = it } ?: prefs.remove(LAST_SUBTITLE)
        prefs[LAST_FREE] = state.lastFree
        state.lastAccount?.let { prefs[LAST_ACCOUNT] = it } ?: prefs.remove(LAST_ACCOUNT)
    }

    private const val FAVORITES = "favorites"
    private const val THEME_PREFIX = "theme:"
    private const val COLLECTION_PREFIX = "collection:"

    fun encodeSource(source: CarouselSource): String = when (source) {
        CarouselSource.Favorites -> FAVORITES
        is CarouselSource.Theme -> THEME_PREFIX + source.groupId
        is CarouselSource.Collection -> COLLECTION_PREFIX + source.collectionId
    }

    fun decodeSource(raw: String): CarouselSource? = when {
        raw == FAVORITES -> CarouselSource.Favorites
        raw.startsWith(THEME_PREFIX) -> CarouselSource.Theme(raw.removePrefix(THEME_PREFIX))
        raw.startsWith(COLLECTION_PREFIX) -> CarouselSource.Collection(raw.removePrefix(COLLECTION_PREFIX))
        else -> null
    }
}
