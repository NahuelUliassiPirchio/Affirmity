package com.pirxhio.affirmity.meditation.progressivemusclerelaxation

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Progressive Muscle Relaxation: settling, then a tense/relax pair per muscle group, then a
 * whole-body rest. The per-group cue text differs only by tense-vs-relax, not by group, so this is
 * a flat [MeditationSequence] built by mapping over [ProgressiveMuscleRelaxationConfig.muscleGroups]
 * in plain Kotlin — not a [com.pirxhio.affirmity.meditation.Repeat], since `FixedCountRepetition`
 * can't vary content per iteration and the group list itself is user-configurable, not a fixed count.
 */
data class ProgressiveMuscleRelaxationConfig(
    val settlingMillis: Long = 60_000L,
    val tenseMillis: Long = 5_000L,
    val relaxMillis: Long = 15_000L,
    val muscleGroups: List<String> = listOf("feet", "legs", "abdomen", "hands", "arms", "shoulders", "face"),
    val wholeBodyRestMillis: Long = 120_000L,
    val sleepEnding: Boolean = false,
)

object ProgressiveMuscleRelaxationText {
    const val SETTLING = "meditation.progressivemusclerelaxation.settling"
    const val TENSE = "meditation.progressivemusclerelaxation.tense"
    const val RELAX = "meditation.progressivemusclerelaxation.relax"
}

fun progressiveMuscleRelaxationMeditationDefinition(
    config: ProgressiveMuscleRelaxationConfig = ProgressiveMuscleRelaxationConfig(),
): MeditationDefinition {
    val children = listOf(
        cuedPhase(
            id = "settling",
            duration = PhaseDuration.Fixed(config.settlingMillis),
            cueTextId = ProgressiveMuscleRelaxationText.SETTLING,
        ),
    ) + config.muscleGroups.flatMap { group ->
        listOf(
            cuedPhase(
                id = "tense_$group",
                duration = PhaseDuration.Fixed(config.tenseMillis),
                cueTextId = ProgressiveMuscleRelaxationText.TENSE,
            ),
            cuedPhase(
                id = "relax_$group",
                duration = PhaseDuration.Fixed(config.relaxMillis),
                cueTextId = ProgressiveMuscleRelaxationText.RELAX,
            ),
        )
    } + listOf(
        restPhase(
            id = "whole_body_rest",
            kind = RestKind.SILENCE,
            duration = PhaseDuration.Fixed(config.wholeBodyRestMillis),
        ),
    )

    return MeditationDefinition(
        id = "progressivemusclerelaxation",
        variables = mapOf("sleepEnding" to config.sleepEnding),
        root = MeditationSequence(id = "progressivemusclerelaxation", children = children),
    )
}
