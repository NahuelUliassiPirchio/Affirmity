package com.pirxhio.affirmity.meditation.trataka

import com.pirxhio.affirmity.meditation.FixedCountRepetition
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Trataka: yogic sustained visual concentration on a single point (traditionally a candle flame),
 * alternating external focus with eyes-closed afterimage observation across a few rounds.
 */
data class TratakaConfig(
    val rounds: Int = 2,
    val preparationMillis: Long = 60_000L,
    val focusMillis: Long = 120_000L,
    val afterimageMillis: Long = 60_000L,
    val restMillis: Long = 30_000L,
    /** User-chosen focus object (candle/dot/symbol) -- the concentration cue text is the same
     * regardless (no per-object cue variant exists), so this has no engine effect yet; recorded
     * in [MeditationDefinition.variables] only. */
    val focusObject: String = "candle",
)

object TratakaText {
    const val PREPARATION = "meditation.trataka.preparation"
    const val EXTERNAL_FOCUS = "meditation.trataka.external_focus"
    const val EYES_CLOSED = "meditation.trataka.eyes_closed"
}

fun tratakaMeditationDefinition(
    config: TratakaConfig = TratakaConfig(),
): MeditationDefinition {
    val children = listOf(
        cuedPhase(
            id = "preparation",
            duration = PhaseDuration.Fixed(config.preparationMillis),
            cueTextId = TratakaText.PREPARATION,
        ),
        Repeat(
            id = "rounds",
            child = MeditationSequence(
                id = "round",
                children = listOf(
                    cuedPhase(
                        id = "external_focus",
                        duration = PhaseDuration.Fixed(config.focusMillis),
                        cueTextId = TratakaText.EXTERNAL_FOCUS,
                    ),
                    cuedPhase(
                        id = "eyes_closed",
                        duration = PhaseDuration.Fixed(config.afterimageMillis),
                        cueTextId = TratakaText.EYES_CLOSED,
                    ),
                ),
            ),
            strategy = FixedCountRepetition(config.rounds),
        ),
        restPhase(
            id = "rest",
            kind = RestKind.SILENCE,
            duration = PhaseDuration.Fixed(config.restMillis),
        ),
    )

    return MeditationDefinition(
        id = "trataka",
        variables = mapOf("focusObject" to config.focusObject),
        root = MeditationSequence(id = "trataka", children = children),
    )
}
