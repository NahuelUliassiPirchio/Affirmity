package com.pirxhio.affirmity.meditation.wimhof.voice

import com.pirxhio.affirmity.meditation.customization.localeMatches
import kotlin.random.Random

/** Customization key injected by [resolveVoiceGuide] and read when building the session. */
const val VOICE_GUIDE_KEY = "voiceGuide"

/** Locales that ship Wim Hof voice clips. */
val VOICE_GUIDE_LOCALES: Set<String> = setOf("es")

/**
 * The single gate for the voice guide: on only for a locale that ships the clips, and then
 * unless the stored toggle says otherwise. The result is injected into the customization map, so
 * the rest of the session reads one resolved value.
 */
fun resolveVoiceGuide(customization: Map<String, String>, languageTag: String?): Map<String, String> {
    val enabled = localeMatches(VOICE_GUIDE_LOCALES, languageTag) &&
        (customization[VOICE_GUIDE_KEY]?.toBooleanStrictOrNull() ?: true)
    return customization + (VOICE_GUIDE_KEY to enabled.toString())
}

/** Pure scheduling rules of the voice guide. */
object WhmVoicePolicy {
    const val RESP_MIN_GAP_CYCLES = 4
    const val RESP_MAX_GAP_CYCLES = 6
    private const val RESP_LAST_BREATHS_QUIET = 8

    const val RET_MIN_GAP_MILLIS = 20_000
    const val RET_MAX_GAP_MILLIS = 30_000

    /** An optional clip must end this long before the next required mark. */
    const val OPTIONAL_CLIP_MARGIN_MILLIS = 300L

    /** REC_CUENTA starts here inside the recovery hold. */
    const val REC_CUENTA_AT_MILLIS = 12_720L

    const val RESP_CLIP_COUNT = 12
    const val RET_CLIP_COUNT = 11

    fun respGapCycles(random: Random): Int = random.nextInt(RESP_MIN_GAP_CYCLES, RESP_MAX_GAP_CYCLES + 1)

    fun retGapMillis(random: Random): Long = random.nextInt(RET_MIN_GAP_MILLIS, RET_MAX_GAP_MILLIS + 1).toLong()

    /** [breathIndex] is the 0-based inhale being entered; 5 breaths remain at N-5, the last is N-1. */
    fun countdownClip(breathIndex: Int, breathsPerRound: Int): String? = when (breathIndex) {
        breathsPerRound - 5 -> "whm_vo_cuenta_5"
        breathsPerRound - 1 -> "whm_vo_cuenta_ultima"
        else -> null
    }

    fun respAllowed(breathIndex: Int, breathsPerRound: Int): Boolean =
        breathIndex < breathsPerRound - RESP_LAST_BREATHS_QUIET

    fun fitsBefore(startMillis: Long, clipMillis: Long, nextRequiredMillis: Long?): Boolean =
        nextRequiredMillis == null || startMillis + clipMillis + OPTIONAL_CLIP_MARGIN_MILLIS <= nextRequiredMillis

    fun respClipId(n: Int): String = "whm_vo_resp_%02d".format(n)

    fun retClipId(n: Int): String = "whm_vo_ret_%02d".format(n)

    const val RET_INICIO = "whm_vo_ret_inicio"
    const val REC_INICIO = "whm_vo_rec_inicio"
    const val REC_CUENTA = "whm_vo_rec_cuenta"
}
