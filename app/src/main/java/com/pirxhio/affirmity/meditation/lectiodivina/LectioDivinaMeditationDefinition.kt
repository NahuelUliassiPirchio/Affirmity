package com.pirxhio.affirmity.meditation.lectiodivina

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Lectio Divina: the traditional four stages — lectio (reading), meditatio (reflection), oratio
 * (prayer), contemplatio (silence) — each defaulting to the spec's `minutesPerStage` values
 * (3/4/3/5 min). `contemplatio` carries no cue, matching how this catalog's other terminal-silence
 * spans (e.g. centering prayer) are modelled with `RestKind.SILENCE`.
 */
data class LectioDivinaConfig(
    val lectioMillis: Long = 180_000L,
    val meditatioMillis: Long = 240_000L,
    val oratioMillis: Long = 180_000L,
    val contemplatioMillis: Long = 300_000L,
    /** User-supplied passage and whether prompts are guided -- the four stage cues are fixed
     * regardless (no per-passage cue variant and no unguided-cue alternative exist), so these
     * have no engine effect yet; recorded in [MeditationDefinition.variables] only, mirroring
     * `dhikrPhrase` in [com.pirxhio.affirmity.meditation.dhikr.DhikrConfig]. */
    val passage: String? = null,
    val guidedPrompts: Boolean = true,
)

object LectioDivinaText {
    const val LECTIO = "meditation.lectiodivina.lectio"
    const val MEDITATIO = "meditation.lectiodivina.meditatio"
    const val ORATIO = "meditation.lectiodivina.oratio"
}

fun lectioDivinaMeditationDefinition(
    config: LectioDivinaConfig = LectioDivinaConfig(),
): MeditationDefinition {
    val children = listOf(
        cuedPhase(
            id = "lectio",
            duration = PhaseDuration.Fixed(config.lectioMillis),
            cueTextId = LectioDivinaText.LECTIO,
        ),
        cuedPhase(
            id = "meditatio",
            duration = PhaseDuration.Fixed(config.meditatioMillis),
            cueTextId = LectioDivinaText.MEDITATIO,
        ),
        cuedPhase(
            id = "oratio",
            duration = PhaseDuration.Fixed(config.oratioMillis),
            cueTextId = LectioDivinaText.ORATIO,
        ),
        restPhase(
            id = "contemplatio",
            kind = RestKind.SILENCE,
            duration = PhaseDuration.Fixed(config.contemplatioMillis),
        ),
    )

    return MeditationDefinition(
        id = "lectiodivina",
        variables = mapOf(
            "passage" to config.passage,
            "guidedPrompts" to config.guidedPrompts,
        ),
        root = MeditationSequence(id = "lectiodivina", children = children),
    )
}
