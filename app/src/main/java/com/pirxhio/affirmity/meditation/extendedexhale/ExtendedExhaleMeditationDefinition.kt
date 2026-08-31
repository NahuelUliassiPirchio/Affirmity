package com.pirxhio.affirmity.meditation.extendedexhale

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.authoring.breathingBlock

/** A plain inhale/exhale block where the exhale outlasts the inhale, repeated [rounds] times.
 * Uses the shared inhale/exhale cues — no meditation-specific text of its own. */
data class ExtendedExhaleConfig(
    val rounds: Int = 10,
    val inhaleMillis: Long = 4_000L,
    val exhaleMillis: Long = 6_000L,
)

fun extendedExhaleMeditationDefinition(
    config: ExtendedExhaleConfig = ExtendedExhaleConfig(),
): MeditationDefinition {
    require(config.rounds > 0) { "rounds must be > 0, got ${config.rounds}" }

    return MeditationDefinition(
        id = "extendedexhale",
        variables = mapOf("rounds" to config.rounds),
        root = breathingBlock(
            id = "breathing",
            breaths = config.rounds,
            inhaleMillis = config.inhaleMillis,
            exhaleMillis = config.exhaleMillis,
        ),
    )
}
