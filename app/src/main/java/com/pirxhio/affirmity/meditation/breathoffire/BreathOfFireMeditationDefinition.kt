package com.pirxhio.affirmity.meditation.breathoffire

import com.pirxhio.affirmity.meditation.FixedCountRepetition
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.rapidBreathCyclePhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/** Preparation, then rapid equal-paced breathing, then recovery — repeated [rounds] times.
 * [pace] is recorded in [MeditationDefinition.variables] for presentation/analytics purposes only
 * -- the engine's phases are duration-based, so pace does not currently change [activeMillis]'s
 * internal rhythm (a future stage could vary [com.pirxhio.affirmity.meditation.authoring.rapidBreathCyclePhase]'s
 * ambient cue by pace; not modeled here). */
data class BreathOfFireConfig(
    val rounds: Int = 3,
    val preparationMillis: Long = 30_000L,
    val activeMillis: Long = 30_000L,
    val recoveryMillis: Long = 30_000L,
    val pace: String = "beginner",
)

object BreathOfFireText {
    const val PREPARATION = "meditation.breathoffire.preparation"
    const val ACTIVE = "meditation.breathoffire.active"
}

object BreathOfFireAudio {
    const val ACTIVE_AMBIENT = "meditation.breathoffire.active_ambient"
}

fun breathOfFireMeditationDefinition(config: BreathOfFireConfig = BreathOfFireConfig()): MeditationDefinition {
    require(config.rounds > 0) { "rounds must be > 0, got ${config.rounds}" }

    val round = MeditationSequence(
        id = "round",
        children = listOf(
            cuedPhase(
                id = "preparation",
                duration = PhaseDuration.Fixed(config.preparationMillis),
                cueTextId = BreathOfFireText.PREPARATION,
            ),
            rapidBreathCyclePhase(
                id = "fire_breath",
                duration = PhaseDuration.Fixed(config.activeMillis),
                ambientAudioId = BreathOfFireAudio.ACTIVE_AMBIENT,
                cueTextId = BreathOfFireText.ACTIVE,
            ),
            restPhase(
                id = "recovery",
                kind = RestKind.SILENCE,
                duration = PhaseDuration.Fixed(config.recoveryMillis),
            ),
        ),
    )

    return MeditationDefinition(
        id = "breathoffire",
        variables = mapOf("rounds" to config.rounds, "pace" to config.pace),
        root = Repeat(
            id = "rounds",
            child = round,
            strategy = FixedCountRepetition(config.rounds),
        ),
    )
}
