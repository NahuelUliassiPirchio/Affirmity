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

/** Text that is safe to persist and replay: only ever taken from a free-tier frame (D3b). */
data class LastShown(val text: String, val subtitle: String?)

/**
 * Null for any frame that is not free for everyone, so gated text can never be replayed from
 * prefs after a Pro lapse or an expired ad-unlock (R5.3, S5.5).
 */
fun persistedLast(frame: CarouselFrame): LastShown? =
    if (frame.isFreeTier) LastShown(frame.text, frame.subtitle) else null

/** Per-instance state (spec R4), mirrored 1:1 by [CarouselStateCodec]. */
data class CarouselState(
    val source: CarouselSource? = null,
    val order: List<String> = emptyList(),
    val cursor: Int = 0,
    val advancePending: Boolean = false,
    val fallback: Boolean = false,
    val lastText: String? = null,
    val lastSubtitle: String? = null,
    val lastFree: Boolean = false,
) {
    val queue: QueueState get() = QueueState(order, cursor)

    /** Records [frame] as the last shown one; clears stored text unless the frame is free. */
    fun withShown(frame: CarouselFrame): CarouselState {
        val last = persistedLast(frame)
        return copy(lastText = last?.text, lastSubtitle = last?.subtitle, lastFree = last != null)
    }

    /** The stored text, only when it was marked free AND text exists (S6.3, S6.4). */
    fun replayableLast(): LastShown? =
        if (lastFree && lastText != null) LastShown(lastText, lastSubtitle) else null
}

/** Maps [CarouselState] to and from Glance's per-widget [Preferences] store (design D3). */
object CarouselStateCodec {
    val SOURCE = stringPreferencesKey("source")
    val ORDER = stringPreferencesKey("order")
    val CURSOR = intPreferencesKey("cursor")
    val ADVANCE_PENDING = booleanPreferencesKey("advancePending")
    val FALLBACK = booleanPreferencesKey("fallback")
    val LAST_TEXT = stringPreferencesKey("lastText")
    val LAST_SUBTITLE = stringPreferencesKey("lastSubtitle")
    val LAST_FREE = booleanPreferencesKey("lastFree")

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
