package com.pirxhio.affirmity.meditation.noting

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Noting: a Vipassana-derived mindfulness technique — briefly labeling experiences (thinking,
 * hearing, feeling, ...) before letting them pass. The label rotation is presentation-layer flavor
 * text on a single continuous span, not engine machinery (the engine has no reason to know about
 * label rotation) — [NotingText.NOTING]'s cue names the labels the practitioner cycles through.
 * `120 + 420 + 60 = 600s` = 10 min exact.
 */
data class NotingConfig(
    val breathAnchorMillis: Long = 120_000L,
    val notingMillis: Long = 420_000L,
    val openAwarenessMillis: Long = 60_000L,
    val labels: Set<String> = setOf("thinking", "hearing", "feeling"),
    val guidanceLevel: String = "full",
)

object NotingText {
    const val BREATH_ANCHOR = "meditation.noting.breath_anchor"
    const val NOTING = "meditation.noting.noting"
    const val OPEN = "meditation.noting.open"
}

fun notingMeditationDefinition(
    config: NotingConfig = NotingConfig(),
): MeditationDefinition {
    val children = listOf(
        restPhase(
            id = "breath_anchor",
            kind = RestKind.BREATH_AWARENESS,
            duration = PhaseDuration.Fixed(config.breathAnchorMillis),
            cueTextId = NotingText.BREATH_ANCHOR,
        ),
        restPhase(
            id = "noting",
            kind = RestKind.OPEN_AWARENESS,
            duration = PhaseDuration.Fixed(config.notingMillis),
            cueTextId = NotingText.NOTING,
        ),
        restPhase(
            id = "open_awareness",
            kind = RestKind.OPEN_AWARENESS,
            duration = PhaseDuration.Fixed(config.openAwarenessMillis),
            cueTextId = NotingText.OPEN,
        ),
    )

    return MeditationDefinition(
        id = "noting",
        variables = mapOf("labels" to config.labels, "guidanceLevel" to config.guidanceLevel),
        root = MeditationSequence(id = "noting", children = children),
    )
}
