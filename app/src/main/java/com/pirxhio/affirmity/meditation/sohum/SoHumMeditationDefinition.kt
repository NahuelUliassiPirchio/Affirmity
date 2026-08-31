package com.pirxhio.affirmity.meditation.sohum

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.authoring.breathingBlock

/**
 * So Hum: inhale carries the "So" syllable, exhale carries "Hum". Same shape as `breath_mantra` in
 * the spec — Stage 1 confirmed this needs no new primitive, just [breathingBlock] with the mantra
 * syllables passed as the inhale/exhale text cues instead of the generic "inhalá"/"exhalá" cues.
 * [breaths] is picked (67) to land close to the spec's 10-minute default at a natural ~9s/breath
 * pace (4s inhale + 5s exhale), not a claim about traditional pacing.
 */
data class SoHumConfig(
    val breaths: Int = 67,
    val inhaleMillis: Long = 4_000L,
    val exhaleMillis: Long = 5_000L,
    /** `"mental" | "whispered" | "spoken"` -- no engine-level effect yet, recorded in
     * [MeditationDefinition.variables] only. */
    val mantraVolume: String = "mental",
)

object SoHumText {
    const val SO = "meditation.sohum.so"
    const val HUM = "meditation.sohum.hum"
}

fun soHumMeditationDefinition(
    config: SoHumConfig = SoHumConfig(),
): MeditationDefinition {
    require(config.breaths > 0) { "breaths must be > 0, got ${config.breaths}" }

    return MeditationDefinition(
        id = "sohum",
        variables = mapOf("breaths" to config.breaths, "mantraVolume" to config.mantraVolume),
        root = breathingBlock(
            id = "breathing",
            breaths = config.breaths,
            inhaleMillis = config.inhaleMillis,
            exhaleMillis = config.exhaleMillis,
            inhaleTextId = SoHumText.SO,
            exhaleTextId = SoHumText.HUM,
        ),
    )
}
