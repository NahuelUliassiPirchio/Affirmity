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
    /** UX-audit item 7 fix: this used to have zero engine effect (recorded in [MeditationDefinition
     * .variables] only, while the visualization cue was one fixed generic string) -- the user
     * configured a scenario the session then visibly ignored. Now selects among
     * [VisualizationText]'s per-scenario visualization cues below. An unrecognized value falls
     * back to [VisualizationText.VISUALIZATION_NATURE], same as the `"nature"` default. */
    val scenario: String = "nature",
)

object VisualizationText {
    const val RELAXATION = "meditation.visualization.relaxation"
    const val VISUALIZATION_NATURE = "meditation.visualization.visualization.nature"
    const val VISUALIZATION_SAFE_PLACE = "meditation.visualization.visualization.safe_place"
    const val VISUALIZATION_GOAL = "meditation.visualization.visualization.goal"
    const val VISUALIZATION_PERFORMANCE = "meditation.visualization.visualization.performance"
    const val VISUALIZATION_CUSTOM = "meditation.visualization.visualization.custom"
    const val INTEGRATION = "meditation.visualization.integration"
    const val RETURN = "meditation.visualization.return"

    /** UX-audit item 7: `scenario` -> the cue text id that actually narrates that scenario,
     * mirroring the `optionLabelRes` `when` MeditationCatalog already uses to label the same
     * values on the customization screen. Unknown/default falls back to nature. */
    fun visualizationCueFor(scenario: String): String = when (scenario) {
        "safe_place" -> VISUALIZATION_SAFE_PLACE
        "goal" -> VISUALIZATION_GOAL
        "performance" -> VISUALIZATION_PERFORMANCE
        "custom" -> VISUALIZATION_CUSTOM
        else -> VISUALIZATION_NATURE
    }
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
            cueTextId = VisualizationText.visualizationCueFor(config.scenario),
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
        ),
        root = MeditationSequence(id = "visualization", children = children),
    )
}
