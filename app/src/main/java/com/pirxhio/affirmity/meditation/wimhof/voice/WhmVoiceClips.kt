package com.pirxhio.affirmity.meditation.wimhof.voice

import com.pirxhio.affirmity.data.catalog.CatalogLocale

/**
 * The Wim Hof voice clips of one locale: their ids and measured durations.
 *
 * A clip id is the raw resource name, and the locale lives in it: Spanish keeps the original
 * `whm_vo_<stem>` names, English uses `whm_vo_en_<stem>`. The set is picked by the app's resolved
 * [CatalogLocale] (never by Android resource qualifiers) so the in-app locale and the Spanish
 * fallback decide what plays. Every length derived from clips must come from one instance, so a
 * session never mixes the two languages.
 */
class WhmVoiceClips private constructor(
    val locale: CatalogLocale,
    private val prefix: String,
    /** Voice clip id -> duration in milliseconds. */
    val voiceMillis: Map<String, Long>,
) {
    /** A locale with no clips behaves as voice off (see [VOICE_GUIDE_LOCALES]). */
    val isAvailable: Boolean get() = voiceMillis.isNotEmpty()

    /** The clip id of [stem] (`resp_01`, `ret_inicio`, ...) in this locale. */
    fun id(stem: String): String = prefix + stem

    /** Duration of a voice clip of this locale, or of a shared SFX clip. */
    fun durationMillis(id: String): Long? = voiceMillis[id] ?: WhmClips.sfxMillis[id]

    val respCount: Int = countOf("resp_")
    val retCount: Int = countOf("ret_")

    /** Hold time marks (millis into the hold) and the TIEMPO clip of this locale, in order. */
    val tiempoMarks: List<Pair<Long, String>> =
        WhmClips.tiempoBySeconds.toSortedMap().map { (seconds, spanishId) ->
            seconds * 1_000L to id(spanishId.removePrefix(SPANISH_PREFIX))
        }

    private fun countOf(stemPrefix: String): Int {
        val numbered = Regex("${stemPrefix}\\d+")
        return voiceMillis.keys.count { it.removePrefix(prefix).matches(numbered) }
    }

    companion object {
        private const val SPANISH_PREFIX = "whm_vo_"

        private val es = WhmVoiceClips(CatalogLocale.ES, SPANISH_PREFIX, WhmClips.voiceMillis)
        private val en = WhmVoiceClips(CatalogLocale.EN, "whm_vo_en_", WhmClipsEn.voiceMillis)

        fun of(locale: CatalogLocale): WhmVoiceClips = when (locale) {
            CatalogLocale.ES -> es
            CatalogLocale.EN -> en
        }
    }
}
