package com.pirxhio.affirmity.meditation.bhramari

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.authoring.breathingBlock

/** Inhale, then a long humming exhale, repeated [rounds] times. */
data class BhramariConfig(
    val rounds: Int = 7,
    val inhaleMillis: Long = 4_000L,
    val exhaleMillis: Long = 8_000L,
)

object BhramariText {
    const val HUMMING_EXHALE = "meditation.bhramari.humming_exhale"
}

fun bhramariMeditationDefinition(config: BhramariConfig = BhramariConfig()): MeditationDefinition {
    require(config.rounds > 0) { "rounds must be > 0, got ${config.rounds}" }

    return MeditationDefinition(
        id = "bhramari",
        variables = mapOf("rounds" to config.rounds),
        root = breathingBlock(
            id = "breathing",
            breaths = config.rounds,
            inhaleMillis = config.inhaleMillis,
            exhaleMillis = config.exhaleMillis,
            exhaleTextId = BhramariText.HUMMING_EXHALE,
        ),
    )
}
