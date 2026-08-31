package com.pirxhio.affirmity.meditation.breathing478

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.authoring.breathingBlock

/** 4s inhale / 7s hold / 8s exhale, repeated [rounds] times. */
data class Breathing478Config(
    val rounds: Int = 4,
    val inhaleMillis: Long = 4_000L,
    val holdMillis: Long = 7_000L,
    val exhaleMillis: Long = 8_000L,
)

object Breathing478Text {
    const val HOLD = "meditation.breathing478.hold"
}

fun breathing478MeditationDefinition(config: Breathing478Config = Breathing478Config()): MeditationDefinition {
    require(config.rounds > 0) { "rounds must be > 0, got ${config.rounds}" }

    return MeditationDefinition(
        id = "breathing478",
        variables = mapOf("rounds" to config.rounds),
        root = breathingBlock(
            id = "breathing",
            breaths = config.rounds,
            inhaleMillis = config.inhaleMillis,
            exhaleMillis = config.exhaleMillis,
            holdAfterInhaleMillis = config.holdMillis,
            holdTextId = Breathing478Text.HOLD,
        ),
    )
}
