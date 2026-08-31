package com.pirxhio.affirmity.meditation.muraqabah

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Muraqabah: an Islamic Sufi contemplative practice — a spoken intention, settling into
 * breath-awareness, then extended silent contemplation.
 */
data class MuraqabahConfig(
    val intentionMillis: Long = 60_000L,
    val breathSettlingMillis: Long = 120_000L,
    val contemplationMillis: Long = 420_000L,
    /** `"full" | "light" | "silent"` -- no engine hook yet (no alternate cue-text variants
     * exist), recorded in [MeditationDefinition.variables] only. */
    val guidanceLevel: String = "light",
)

object MuraqabahText {
    const val INTENTION = "meditation.muraqabah.intention"
    const val BREATH_SETTLING = "meditation.muraqabah.breath_settling"
}

fun muraqabahMeditationDefinition(
    config: MuraqabahConfig = MuraqabahConfig(),
): MeditationDefinition {
    val children = listOf(
        cuedPhase(
            id = "intention",
            duration = PhaseDuration.Fixed(config.intentionMillis),
            cueTextId = MuraqabahText.INTENTION,
        ),
        restPhase(
            id = "breath_settling",
            kind = RestKind.BREATH_AWARENESS,
            duration = PhaseDuration.Fixed(config.breathSettlingMillis),
            cueTextId = MuraqabahText.BREATH_SETTLING,
        ),
        restPhase(
            id = "contemplation",
            kind = RestKind.SILENCE,
            duration = PhaseDuration.Fixed(config.contemplationMillis),
        ),
    )

    return MeditationDefinition(
        id = "muraqabah",
        variables = mapOf("guidanceLevel" to config.guidanceLevel),
        root = MeditationSequence(id = "muraqabah", children = children),
    )
}
