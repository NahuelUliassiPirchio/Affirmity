package com.pirxhio.affirmity.meditation.boxbreathing

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.authoring.breathingBlock

/** Four equal phases — inhale, hold, exhale, hold — repeated [rounds] times. */
data class BoxBreathingConfig(
    val rounds: Int = 6,
    val inhaleMillis: Long = 4_000L,
    val holdFullMillis: Long = 4_000L,
    val exhaleMillis: Long = 4_000L,
    val holdEmptyMillis: Long = 4_000L,
)

object BoxBreathingText {
    const val HOLD = "meditation.boxbreathing.hold"
}

fun boxBreathingMeditationDefinition(config: BoxBreathingConfig = BoxBreathingConfig()): MeditationDefinition {
    require(config.rounds > 0) { "rounds must be > 0, got ${config.rounds}" }

    return MeditationDefinition(
        id = "boxbreathing",
        variables = mapOf("rounds" to config.rounds),
        root = breathingBlock(
            id = "breathing",
            breaths = config.rounds,
            inhaleMillis = config.inhaleMillis,
            exhaleMillis = config.exhaleMillis,
            holdAfterInhaleMillis = config.holdFullMillis,
            holdAfterExhaleMillis = config.holdEmptyMillis,
            holdTextId = BoxBreathingText.HOLD,
        ),
    )
}
