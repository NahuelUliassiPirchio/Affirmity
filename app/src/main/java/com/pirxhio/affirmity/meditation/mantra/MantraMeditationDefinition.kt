package com.pirxhio.affirmity.meditation.mantra

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Generic mantra meditation: preparation, a duration-bound mantra repetition span, closing
 * silence. The spec's `repeat_mantra` step is duration-based (not a fixed repeat count), so it
 * maps to [cuedPhase] rather than [com.pirxhio.affirmity.meditation.authoring.countedRepetitionPhase]
 * (Stage 1's mapping). `mantra`/`repetitionMode` customization is deferred.
 */
data class MantraConfig(
    val preparationMillis: Long = 60_000L,
    val mantraMillis: Long = 480_000L,
    val silenceMillis: Long = 60_000L,
    /** `"so_hum" | "om" | "custom"`. Which mantra text displays during the mantra phase is a
     * per-entry [com.pirxhio.affirmity.ui.meditation.catalog.MeditationPresentation] concern the
     * engine doesn't drive dynamically yet -- recorded in [MeditationDefinition.variables] only. */
    val mantra: String = "so_hum",
    val customMantra: String? = null,
    val repetitionMode: String = "mental",
)

object MantraMeditationText {
    const val PREPARATION = "meditation.mantra.preparation"
    const val MANTRA = "meditation.mantra.mantra"
}

fun mantraMeditationDefinition(
    config: MantraConfig = MantraConfig(),
): MeditationDefinition {
    val children = listOf(
        cuedPhase(
            id = "preparation",
            duration = PhaseDuration.Fixed(config.preparationMillis),
            cueTextId = MantraMeditationText.PREPARATION,
        ),
        cuedPhase(
            id = "mantra",
            duration = PhaseDuration.Fixed(config.mantraMillis),
            cueTextId = MantraMeditationText.MANTRA,
        ),
        restPhase(
            id = "silence",
            kind = RestKind.SILENCE,
            duration = PhaseDuration.Fixed(config.silenceMillis),
        ),
    )

    return MeditationDefinition(
        id = "mantra",
        variables = mapOf(
            "mantra" to config.mantra,
            "customMantra" to config.customMantra,
            "repetitionMode" to config.repetitionMode,
        ),
        root = MeditationSequence(id = "mantra", children = children),
    )
}
