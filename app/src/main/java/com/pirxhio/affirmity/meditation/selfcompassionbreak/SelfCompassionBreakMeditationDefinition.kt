package com.pirxhio.affirmity.meditation.selfcompassionbreak

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Self-Compassion Break: Kristin Neff's three-step secular practice — recognizing a difficulty,
 * naming shared humanity, then offering yourself kindness — closed with a brief silent
 * integration. Deliberately a separate catalog entry from `selfcompassion5`/`selfcompassion10`
 * (`com.pirxhio.affirmity.meditation.selfcompassion`): that pair is REQ-4.11.6/4.11.7's
 * intro/breathing/phrase/closing upsell shape, already shipped and tested; this is a different,
 * shorter practice structure (recognize/shared-humanity/kindness/integration) that happens to
 * share a name in the source spec, not a replacement for it.
 */
data class SelfCompassionBreakConfig(
    val recognizeMillis: Long = 90_000L,
    val sharedHumanityMillis: Long = 90_000L,
    val kindnessMillis: Long = 180_000L,
    val integrationMillis: Long = 60_000L,
    /** User-supplied kindness phrase and guidance level -- no engine hook yet (the kindness cue
     * text is fixed, no alternate cue-text variants exist), recorded in
     * [MeditationDefinition.variables] only. */
    val customPhrase: String? = null,
    val guidanceLevel: String = "full",
)

object SelfCompassionBreakText {
    const val RECOGNIZE = "meditation.selfcompassionbreak.recognize"
    const val SHARED_HUMANITY = "meditation.selfcompassionbreak.shared_humanity"
    const val KINDNESS = "meditation.selfcompassionbreak.kindness"
}

fun selfCompassionBreakMeditationDefinition(
    config: SelfCompassionBreakConfig = SelfCompassionBreakConfig(),
): MeditationDefinition {
    val children = listOf(
        cuedPhase(
            id = "recognize",
            duration = PhaseDuration.Fixed(config.recognizeMillis),
            cueTextId = SelfCompassionBreakText.RECOGNIZE,
        ),
        cuedPhase(
            id = "shared_humanity",
            duration = PhaseDuration.Fixed(config.sharedHumanityMillis),
            cueTextId = SelfCompassionBreakText.SHARED_HUMANITY,
        ),
        cuedPhase(
            id = "kindness",
            duration = PhaseDuration.Fixed(config.kindnessMillis),
            cueTextId = SelfCompassionBreakText.KINDNESS,
        ),
        restPhase(
            id = "integration",
            kind = RestKind.SILENCE,
            duration = PhaseDuration.Fixed(config.integrationMillis),
        ),
    )

    return MeditationDefinition(
        id = "selfcompassionbreak",
        variables = mapOf(
            "customPhrase" to config.customPhrase,
            "guidanceLevel" to config.guidanceLevel,
        ),
        root = MeditationSequence(id = "selfcompassionbreak", children = children),
    )
}
