package com.pirxhio.affirmity.meditation.dhikr

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.countedRepetitionPhase
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Dhikr: an Islamic remembrance practice — a spoken intention, a counted repetition (traditionally
 * 33 or 99), then closing silence. The specific phrase is left to the practitioner (spec:
 * `dhikrPhrase` default `null`, `allowSupportedTraditionalOptions`), so the repetition cue is a
 * neutral prompt rather than a fixed doctrinal phrase.
 */
data class DhikrConfig(
    val repetitions: Int = 33,
    val repetitionMillis: Long = 2_000L,
    val intentionMillis: Long = 60_000L,
    val silenceMillis: Long = 120_000L,
    /** User-supplied phrase and voicing mode -- the repetition cue stays a neutral prompt
     * regardless (see class doc), so these have no engine effect yet; recorded in
     * [MeditationDefinition.variables] only, mirroring `mantra`/`repetitionMode` in
     * [com.pirxhio.affirmity.meditation.mantra.MantraConfig]. */
    val dhikrPhrase: String? = null,
    val repetitionMode: String = "spoken",
)

object DhikrText {
    const val INTENTION = "meditation.dhikr.intention"
    const val REPETITION = "meditation.dhikr.repetition"
}

fun dhikrMeditationDefinition(
    config: DhikrConfig = DhikrConfig(),
): MeditationDefinition {
    val children = listOf(
        cuedPhase(
            id = "intention",
            duration = PhaseDuration.Fixed(config.intentionMillis),
            cueTextId = DhikrText.INTENTION,
        ),
        countedRepetitionPhase(
            id = "repetition",
            count = config.repetitions,
            repetitionDurationMillis = config.repetitionMillis,
            cueTextId = DhikrText.REPETITION,
        ),
        restPhase(
            id = "silence",
            kind = RestKind.SILENCE,
            duration = PhaseDuration.Fixed(config.silenceMillis),
        ),
    )

    return MeditationDefinition(
        id = "dhikr",
        variables = mapOf(
            "dhikrPhrase" to config.dhikrPhrase,
            "repetitionMode" to config.repetitionMode,
        ),
        root = MeditationSequence(id = "dhikr", children = children),
    )
}
