package com.pirxhio.affirmity.meditation.coherentbreathing

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.authoring.breathingBlock

/** Slow, even breathing at a fixed rate for [durationMinutes]. [breathsPerMinute] derives the
 * per-breath length (each half taking half of one breath's share of a minute) and, together with
 * [durationMinutes], the number of breaths — no separate "rounds" knob, matching the spec's
 * duration-first framing. */
data class CoherentBreathingConfig(
    val durationMinutes: Int = 5,
    val breathsPerMinute: Int = 6,
) {
    val breathMillis: Long get() = 60_000L / breathsPerMinute
    val breaths: Int get() = durationMinutes * breathsPerMinute
}

fun coherentBreathingMeditationDefinition(
    config: CoherentBreathingConfig = CoherentBreathingConfig(),
): MeditationDefinition {
    require(config.durationMinutes > 0) { "durationMinutes must be > 0, got ${config.durationMinutes}" }
    require(config.breathsPerMinute > 0) { "breathsPerMinute must be > 0, got ${config.breathsPerMinute}" }

    val halfBreathMillis = config.breathMillis / 2

    return MeditationDefinition(
        id = "coherentbreathing",
        variables = mapOf(
            "durationMinutes" to config.durationMinutes,
            "breathsPerMinute" to config.breathsPerMinute,
        ),
        root = breathingBlock(
            id = "breathing",
            breaths = config.breaths,
            inhaleMillis = halfBreathMillis,
            exhaleMillis = halfBreathMillis,
        ),
    )
}
