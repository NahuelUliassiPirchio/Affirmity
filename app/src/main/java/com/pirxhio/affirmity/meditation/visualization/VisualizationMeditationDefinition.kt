package com.pirxhio.affirmity.meditation.visualization

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.cuedPhase

/**
 * Guided Visualization: relaxation, then a mental-imagery span, an integration reflection, and a
 * return. All four spec step types (`guided`/`visualization`/`reflection`) collapse to [cuedPhase]
 * — only the cue differs.
 */
data class VisualizationConfig(
    val relaxationMillis: Long = 120_000L,
    val visualizationMillis: Long = 420_000L,
    val integrationMillis: Long = 120_000L,
    val returnMillis: Long = 60_000L,
    /** User-chosen scenario and optional background sound -- the cue text and audio are the same
     * regardless (no per-scenario cue or ambient-audio variant exists yet), so these have no
     * engine effect yet; recorded in [MeditationDefinition.variables] only. */
    val scenario: String = "nature",
    val backgroundSound: String = "none",
)

object VisualizationText {
    const val RELAXATION = "meditation.visualization.relaxation"
    const val VISUALIZATION = "meditation.visualization.visualization"
    const val INTEGRATION = "meditation.visualization.integration"
    const val RETURN = "meditation.visualization.return"
}

fun visualizationMeditationDefinition(
    config: VisualizationConfig = VisualizationConfig(),
): MeditationDefinition {
    val children = listOf(
        cuedPhase(
            id = "relaxation",
            duration = PhaseDuration.Fixed(config.relaxationMillis),
            cueTextId = VisualizationText.RELAXATION,
        ),
        cuedPhase(
            id = "visualization",
            duration = PhaseDuration.Fixed(config.visualizationMillis),
            cueTextId = VisualizationText.VISUALIZATION,
        ),
        cuedPhase(
            id = "integration",
            duration = PhaseDuration.Fixed(config.integrationMillis),
            cueTextId = VisualizationText.INTEGRATION,
        ),
        cuedPhase(
            id = "return",
            duration = PhaseDuration.Fixed(config.returnMillis),
            cueTextId = VisualizationText.RETURN,
        ),
    )

    return MeditationDefinition(
        id = "visualization",
        variables = mapOf(
            "scenario" to config.scenario,
            "backgroundSound" to config.backgroundSound,
        ),
        root = MeditationSequence(id = "visualization", children = children),
    )
}
