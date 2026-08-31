package com.pirxhio.affirmity.meditation.vipassana

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Vipassana: breath anchor, body-sensation awareness, open observation, closing. The three
 * awareness spans differ only in their cue, per [RestKind]'s own rationale — breath anchor uses
 * [RestKind.BREATH_AWARENESS], the other two use [RestKind.OPEN_AWARENESS] since neither
 * "body_sensations" nor "experience" is breath-specific.
 */
data class VipassanaConfig(
    val breathAnchorMillis: Long = 180_000L,
    val bodyAwarenessMillis: Long = 300_000L,
    val openObservationMillis: Long = 300_000L,
    val closingMillis: Long = 60_000L,
    /** No engine-level effect yet -- recorded in [MeditationDefinition.variables] only. */
    val guidanceLevel: String = "full",
)

object VipassanaText {
    const val BREATH_ANCHOR = "meditation.vipassana.breath_anchor"
    const val BODY_AWARENESS = "meditation.vipassana.body_awareness"
    const val OPEN_OBSERVATION = "meditation.vipassana.open_observation"
    const val CLOSING = "meditation.vipassana.closing"
}

fun vipassanaMeditationDefinition(
    config: VipassanaConfig = VipassanaConfig(),
): MeditationDefinition {
    val children = listOf(
        restPhase(
            id = "breath_anchor",
            kind = RestKind.BREATH_AWARENESS,
            duration = PhaseDuration.Fixed(config.breathAnchorMillis),
            cueTextId = VipassanaText.BREATH_ANCHOR,
        ),
        restPhase(
            id = "body_awareness",
            kind = RestKind.OPEN_AWARENESS,
            duration = PhaseDuration.Fixed(config.bodyAwarenessMillis),
            cueTextId = VipassanaText.BODY_AWARENESS,
        ),
        restPhase(
            id = "open_observation",
            kind = RestKind.OPEN_AWARENESS,
            duration = PhaseDuration.Fixed(config.openObservationMillis),
            cueTextId = VipassanaText.OPEN_OBSERVATION,
        ),
        cuedPhase(
            id = "closing",
            duration = PhaseDuration.Fixed(config.closingMillis),
            cueTextId = VipassanaText.CLOSING,
        ),
    )

    return MeditationDefinition(
        id = "vipassana",
        variables = mapOf("guidanceLevel" to config.guidanceLevel),
        root = MeditationSequence(id = "vipassana", children = children),
    )
}
