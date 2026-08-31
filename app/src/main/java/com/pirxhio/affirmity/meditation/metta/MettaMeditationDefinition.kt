package com.pirxhio.affirmity.meditation.metta

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.cuedPhase

/**
 * Loving-kindness (metta): sequential reflections, one per selected target, always emitted in the
 * canonical spec order (self, loved_one, neutral_person, difficult_person, all_beings) regardless
 * of [targets]' iteration order -- selection only filters which of the five run, it never reorders
 * them. [traditionalLanguage] has no engine-level effect yet (no alternate cue-text variant is
 * wired) -- recorded in [MeditationDefinition.variables] only.
 */
data class MettaConfig(
    val secondsPerTargetMillis: Long = 120_000L,
    val targets: Set<String> = TARGET_ORDER.toSet(),
    val traditionalLanguage: Boolean = false,
)

object MettaText {
    const val SELF = "meditation.metta.self"
    const val LOVED_ONE = "meditation.metta.loved_one"
    const val NEUTRAL_PERSON = "meditation.metta.neutral_person"
    const val DIFFICULT_PERSON = "meditation.metta.difficult_person"
    const val ALL_BEINGS = "meditation.metta.all_beings"
}

val TARGET_ORDER: List<String> =
    listOf("self", "loved_one", "neutral_person", "difficult_person", "all_beings")

private val TARGET_TEXT: Map<String, String> = mapOf(
    "self" to MettaText.SELF,
    "loved_one" to MettaText.LOVED_ONE,
    "neutral_person" to MettaText.NEUTRAL_PERSON,
    "difficult_person" to MettaText.DIFFICULT_PERSON,
    "all_beings" to MettaText.ALL_BEINGS,
)

fun mettaMeditationDefinition(
    config: MettaConfig = MettaConfig(),
): MeditationDefinition {
    val selected = TARGET_ORDER.filter { it in config.targets }
    require(selected.isNotEmpty()) { "targets must include at least one of $TARGET_ORDER" }

    val duration = PhaseDuration.Fixed(config.secondsPerTargetMillis)
    val children = selected.map { target ->
        cuedPhase(id = target, duration = duration, cueTextId = requireNotNull(TARGET_TEXT[target]))
    }

    return MeditationDefinition(
        id = "metta",
        variables = mapOf("traditionalLanguage" to config.traditionalLanguage),
        root = MeditationSequence(id = "metta", children = children),
    )
}
