package com.pirxhio.affirmity.meditation.om

import com.pirxhio.affirmity.meditation.FixedCountRepetition
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.authoring.chantPhase
import com.pirxhio.affirmity.meditation.breathing.BreathingText

/** Om meditation: inhale, then chant "Om" on the exhale, repeated [rounds] times. */
data class OmConfig(
    val rounds: Int = 12,
    val inhaleMillis: Long = 4_000L,
    val chantMillis: Long = 8_000L,
    /** `"spoken" | "whispered" | "mental"` -- no engine-level effect yet, recorded in
     * [MeditationDefinition.variables] only. */
    val chantMode: String = "spoken",
)

object OmMeditationText {
    const val CHANT = "meditation.om.chant"
}

object OmMeditationAudio {
    const val CHANT = "meditation.om.chant_audio"
}

fun omMeditationDefinition(
    config: OmConfig = OmConfig(),
): MeditationDefinition {
    require(config.rounds > 0) { "rounds must be > 0, got ${config.rounds}" }

    val cycle = MeditationSequence(
        id = "cycle",
        children = listOf(
            Phase(
                id = "inhale",
                duration = PhaseDuration.Fixed(config.inhaleMillis),
                onEnter = listOf(ShowText(BreathingText.INHALE)),
            ),
            chantPhase(
                id = "om",
                duration = PhaseDuration.Fixed(config.chantMillis),
                audioId = OmMeditationAudio.CHANT,
                cueTextId = OmMeditationText.CHANT,
            ),
        ),
    )

    return MeditationDefinition(
        id = "om",
        variables = mapOf("rounds" to config.rounds, "chantMode" to config.chantMode),
        root = Repeat(
            id = "rounds",
            child = cycle,
            strategy = FixedCountRepetition(config.rounds),
        ),
    )
}
