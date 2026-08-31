package com.pirxhio.affirmity.meditation.openawareness

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Open Awareness: a secular, Buddhist-inspired practice with no single fixed object — anchoring on
 * the breath, then expanding into a wide field where sounds, sensations and thoughts are simply
 * noticed. `120 + 60 + 420 = 600s` = 10 min exact.
 */
data class OpenAwarenessConfig(
    val anchorMillis: Long = 120_000L,
    val expandMillis: Long = 60_000L,
    val openAwarenessMillis: Long = 420_000L,
    /** Recorded in [MeditationDefinition.variables] only -- the engine has no concept of a
     * lighter/silent cue variant yet, so this has no behavioral effect until a future stage
     * adds one. */
    val guidanceLevel: String = "light",
)

object OpenAwarenessText {
    const val ANCHOR = "meditation.openawareness.anchor"
    const val EXPAND = "meditation.openawareness.expand"
    const val OPEN = "meditation.openawareness.open"
}

fun openAwarenessMeditationDefinition(
    config: OpenAwarenessConfig = OpenAwarenessConfig(),
): MeditationDefinition {
    val children = listOf(
        restPhase(
            id = "anchor",
            kind = RestKind.BREATH_AWARENESS,
            duration = PhaseDuration.Fixed(config.anchorMillis),
            cueTextId = OpenAwarenessText.ANCHOR,
        ),
        cuedPhase(
            id = "expand",
            duration = PhaseDuration.Fixed(config.expandMillis),
            cueTextId = OpenAwarenessText.EXPAND,
        ),
        restPhase(
            id = "open_awareness",
            kind = RestKind.OPEN_AWARENESS,
            duration = PhaseDuration.Fixed(config.openAwarenessMillis),
            cueTextId = OpenAwarenessText.OPEN,
        ),
    )

    return MeditationDefinition(
        id = "openawareness",
        variables = mapOf("guidanceLevel" to config.guidanceLevel),
        root = MeditationSequence(id = "openawareness", children = children),
    )
}
