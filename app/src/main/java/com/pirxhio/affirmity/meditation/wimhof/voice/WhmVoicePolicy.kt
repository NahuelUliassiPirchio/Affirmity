package com.pirxhio.affirmity.meditation.wimhof.voice

import com.pirxhio.affirmity.data.catalog.CatalogLocale
import com.pirxhio.affirmity.meditation.customization.localeMatches
import kotlin.random.Random

/**
 * Customization key injected by [resolveVoiceGuide] and read when building the session. The stored
 * toggle is one "I want a voice guide" preference shared by every locale, on purpose.
 */
const val VOICE_GUIDE_KEY = "voiceGuide"

/** Customization key injected by [resolveVoiceGuide]: the tag of the locale whose clips play. */
const val VOICE_LOCALE_KEY = "voiceLocale"

/** Locales that ship Wim Hof voice clips (a locale without clips behaves as voice off). */
val VOICE_GUIDE_LOCALES: Set<String> =
    CatalogLocale.entries.filter { WhmVoiceClips.of(it).isAvailable }.map { it.tag }.toSet()

/**
 * The single gate for the voice guide: on only for a locale that ships the clips, and then
 * unless the stored toggle says otherwise. The result is injected into the customization map, so
 * the rest of the session reads one resolved value, plus [VOICE_LOCALE_KEY] naming the clip set.
 * Each locale plays only its own clips; there is no cross-language fallback.
 */
fun resolveVoiceGuide(customization: Map<String, String>, languageTag: String?): Map<String, String> {
    val available = localeMatches(VOICE_GUIDE_LOCALES, languageTag)
    val enabled = available && (customization[VOICE_GUIDE_KEY]?.toBooleanStrictOrNull() ?: true)
    val resolved = customization + (VOICE_GUIDE_KEY to enabled.toString())
    return if (available) resolved + (VOICE_LOCALE_KEY to CatalogLocale.fromLanguageTag(languageTag).tag) else resolved
}

/** Same gate, driven by the language the app shows (in-app locale, else device, else Spanish). */
fun resolveVoiceGuide(customization: Map<String, String>, locale: CatalogLocale): Map<String, String> =
    resolveVoiceGuide(customization, locale.tag)

/** Pure scheduling rules of the voice guide. */
object WhmVoicePolicy {
    const val RESP_MIN_GAP_CYCLES = 4
    const val RESP_MAX_GAP_CYCLES = 6
    private const val RESP_LAST_BREATHS_QUIET = 8

    const val RET_MIN_GAP_MILLIS = 20_000
    const val RET_MAX_GAP_MILLIS = 30_000

    /** An optional clip must end this long before the next required mark. */
    const val OPTIONAL_CLIP_MARGIN_MILLIS = 300L

    /** Spanish REC_CUENTA offset inside the recovery hold; see [recCuentaAtMillis] for the rule. */
    const val REC_CUENTA_AT_MILLIS = 12_720L

    /** Silence between REC_INICIO and REC_CUENTA: the offset above minus the Spanish REC_INICIO (2_555 ms). */
    private const val REC_SILENCE_MILLIS = 10_165L
    private const val REC_TAIL_MILLIS = 400L
    private const val REC_ROUNDING_MILLIS = 200L

    /** Numbered clips in each locale's generated table, so a re-recorded set never drifts from the policy. */
    fun respClipCount(locale: CatalogLocale): Int = WhmVoiceClips.of(locale).respCount

    fun retClipCount(locale: CatalogLocale): Int = WhmVoiceClips.of(locale).retCount

    fun respGapCycles(random: Random): Int = random.nextInt(RESP_MIN_GAP_CYCLES, RESP_MAX_GAP_CYCLES + 1)

    fun retGapMillis(random: Random): Long = random.nextInt(RET_MIN_GAP_MILLIS, RET_MAX_GAP_MILLIS + 1).toLong()

    /** [breathIndex] is the 0-based inhale being entered; 5 breaths remain at N-5, the last is N-1. */
    fun countdownClip(breathIndex: Int, breathsPerRound: Int, locale: CatalogLocale = CatalogLocale.ES): String? =
        when (breathIndex) {
            breathsPerRound - 5 -> WhmVoiceClips.of(locale).id("cuenta_5")
            breathsPerRound - 1 -> WhmVoiceClips.of(locale).id("cuenta_ultima")
            else -> null
        }

    fun respAllowed(breathIndex: Int, breathsPerRound: Int): Boolean =
        breathIndex < breathsPerRound - RESP_LAST_BREATHS_QUIET

    fun fitsBefore(startMillis: Long, clipMillis: Long, nextRequiredMillis: Long?): Boolean =
        nextRequiredMillis == null || startMillis + clipMillis + OPTIONAL_CLIP_MARGIN_MILLIS <= nextRequiredMillis

    fun respClipId(n: Int, locale: CatalogLocale = CatalogLocale.ES): String =
        WhmVoiceClips.of(locale).id("resp_%02d".format(n))

    fun retClipId(n: Int, locale: CatalogLocale = CatalogLocale.ES): String =
        WhmVoiceClips.of(locale).id("ret_%02d".format(n))

    /** Pause between chained intro clips. */
    const val CHAIN_GAP_MILLIS = 600L

    /** The Spanish chains; [introClips] / [closingClips] give a locale's own. */
    val INTRO_CLIPS: List<String> = introClips(CatalogLocale.ES)
    val CLOSING_CLIPS: List<String> = closingClips(CatalogLocale.ES)

    fun introClips(locale: CatalogLocale): List<String> =
        WhmVoiceClips.of(locale).let { clips -> (1..3).map { clips.id("intro_$it") } }

    fun closingClips(locale: CatalogLocale): List<String> =
        WhmVoiceClips.of(locale).let { clips -> (1..4).map { clips.id("cierre_$it") } }

    /** Silence kept after the last CIERRE clip before the session ends. */
    const val CLOSING_TAIL_MILLIS = 1_000L

    private const val CLOSING_ROUNDING_MILLIS = 500L

    /** Length of a chain: its clips, the gaps between them and a tail, rounded up to 0.5 s. */
    private fun chainMillis(clips: List<String>, locale: CatalogLocale): Long {
        val voice = WhmVoiceClips.of(locale)
        val needed = clips.sumOf { voice.durationMillis(it) ?: 0L } +
            (clips.size - 1) * CHAIN_GAP_MILLIS + CLOSING_TAIL_MILLIS
        return ceilTo(needed, CLOSING_ROUNDING_MILLIS)
    }

    private fun ceilTo(value: Long, step: Long): Long = (value + step - 1) / step * step

    /** Closing phase length, from the CIERRE clips of [locale]. */
    fun closingMillis(locale: CatalogLocale = CatalogLocale.ES): Long = chainMillis(closingClips(locale), locale)

    /** Intro phase length, from the INTRO clips of [locale]. */
    fun introMillis(locale: CatalogLocale = CatalogLocale.ES): Long = chainMillis(introClips(locale), locale)

    /** The REC_CUENTA offset in the recovery hold: REC_INICIO of [locale] plus the silence. */
    fun recCuentaAtMillis(locale: CatalogLocale = CatalogLocale.ES): Long =
        (WhmVoiceClips.of(locale).durationMillis(recInicio(locale)) ?: 0L) + REC_SILENCE_MILLIS

    /** Recovery length with the voice on: REC_CUENTA must end inside it, plus a pad. */
    fun recoveryVoiceMillis(locale: CatalogLocale = CatalogLocale.ES): Long {
        val cuenta = WhmVoiceClips.of(locale).durationMillis(recCuenta(locale)) ?: 0L
        return ceilTo(recCuentaAtMillis(locale) + cuenta + REC_TAIL_MILLIS, REC_ROUNDING_MILLIS)
    }

    /** Round intro length: at least [ROUND_INTRO_MILLIS], longer only if the clips of [locale] would not fit. */
    fun roundIntroMillis(locale: CatalogLocale = CatalogLocale.ES): Long {
        val voice = WhmVoiceClips.of(locale)
        val longestEntre = (1..ENTRE_CLIP_COUNT).maxOf { voice.durationMillis(entreClipId(it, locale)) ?: 0L }
        val longestRonda = (1..MAX_ROUNDS).map { voice.id("ronda_$it") }.plus(voice.id("ronda_final"))
            .maxOf { voice.durationMillis(it) ?: 0L }
        val needed = ENTRE_AT_MILLIS + longestEntre + longestRonda + RONDA_TAIL_MILLIS
        return if (needed <= ROUND_INTRO_MILLIS) ROUND_INTRO_MILLIS else ceilTo(needed, CLOSING_ROUNDING_MILLIS)
    }

    /** Baseline round intro (the Spanish layout). */
    const val ROUND_INTRO_MILLIS = 8_800L

    /** RONDA_1..RONDA_4 exist besides RONDA_FINAL. */
    private const val MAX_ROUNDS = 4

    const val ENTRE_CLIP_COUNT = 3

    /** ENTRE starts just after the bowl has been struck. */
    const val ENTRE_AT_MILLIS = 800L

    /** RONDA_* ends this long before the round intro does. */
    const val RONDA_TAIL_MILLIS = 300L

    fun entreClipId(n: Int, locale: CatalogLocale = CatalogLocale.ES): String =
        WhmVoiceClips.of(locale).id("entre_$n")

    /** [roundIndex] is 0-based; the last round says "final" instead of its number. */
    fun rondaClipId(roundIndex: Int, rounds: Int, locale: CatalogLocale = CatalogLocale.ES): String =
        WhmVoiceClips.of(locale).id(if (roundIndex >= rounds - 1) "ronda_final" else "ronda_${roundIndex + 1}")

    /** Start (inside the round intro) that makes the RONDA clip end [RONDA_TAIL_MILLIS] before the phase ends. */
    fun rondaStartMillis(rondaClipId: String, locale: CatalogLocale = CatalogLocale.ES): Long =
        roundIntroMillis(locale) - RONDA_TAIL_MILLIS - (WhmVoiceClips.of(locale).durationMillis(rondaClipId) ?: 0L)

    fun retInicio(locale: CatalogLocale): String = WhmVoiceClips.of(locale).id("ret_inicio")

    fun recInicio(locale: CatalogLocale): String = WhmVoiceClips.of(locale).id("rec_inicio")

    fun recCuenta(locale: CatalogLocale): String = WhmVoiceClips.of(locale).id("rec_cuenta")
}
