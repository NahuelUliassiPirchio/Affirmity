package com.pirxhio.affirmity.meditation.kapalabhati

import com.pirxhio.affirmity.meditation.FixedCountRepetition
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.rapidBreathCyclePhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Preparation, then rapid active exhalations, then rest — repeated [rounds] times. The spec
 * describes the active span as [breathsPerRound] breaths rather than a duration; this engine's
 * phases are duration-based, so the active phase's length is derived at ~2 breaths/second
 * (500ms/breath) — a judgment call, not a claim about the traditional pace.
 */
data class KapalabhatiConfig(
    val rounds: Int = 3,
    val preparationMillis: Long = 30_000L,
    val breathsPerRound: Int = 30,
    val restMillis: Long = 30_000L,
)

object KapalabhatiText {
    const val PREPARATION = "meditation.kapalabhati.preparation"
    const val ACTIVE = "meditation.kapalabhati.active"
}

object KapalabhatiAudio {
    const val ACTIVE_AMBIENT = "meditation.kapalabhati.active_ambient"
}

private const val MILLIS_PER_BREATH = 500L

fun kapalabhatiMeditationDefinition(config: KapalabhatiConfig = KapalabhatiConfig()): MeditationDefinition {
    require(config.rounds > 0) { "rounds must be > 0, got ${config.rounds}" }
    require(config.breathsPerRound > 0) { "breathsPerRound must be > 0, got ${config.breathsPerRound}" }

    val round = MeditationSequence(
        id = "round",
        children = listOf(
            cuedPhase(
                id = "preparation",
                duration = PhaseDuration.Fixed(config.preparationMillis),
                cueTextId = KapalabhatiText.PREPARATION,
            ),
            rapidBreathCyclePhase(
                id = "active",
                duration = PhaseDuration.Fixed(config.breathsPerRound * MILLIS_PER_BREATH),
                ambientAudioId = KapalabhatiAudio.ACTIVE_AMBIENT,
                cueTextId = KapalabhatiText.ACTIVE,
            ),
            restPhase(
                id = "rest",
                kind = RestKind.SILENCE,
                duration = PhaseDuration.Fixed(config.restMillis),
            ),
        ),
    )

    return MeditationDefinition(
        id = "kapalabhati",
        variables = mapOf("rounds" to config.rounds),
        root = Repeat(
            id = "rounds",
            child = round,
            strategy = FixedCountRepetition(config.rounds),
        ),
    )
}
