package com.pirxhio.affirmity.meditation.yoganidra

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Yoga Nidra: a lying-down guided practice moving through intention (sankalpa), a body-rotation
 * scan, breath awareness, and visualization. The body-rotation phase follows the same six-region
 * `restPhase(BREATH_AWARENESS)` composition as `bodyscan/BodyScanMeditationDefinition.kt` — no
 * shared helper exists for that shape (used by exactly two definitions), so it is repeated inline.
 *
 * `120 + 60 + (6 x 80) + 180 + 240 + 120 = 1200s` = 20 min exact.
 */
data class YogaNidraConfig(
    val settlingMillis: Long = 120_000L,
    val intentionMillis: Long = 60_000L,
    val bodyRegionMillis: Long = 80_000L,
    val breathAwarenessMillis: Long = 180_000L,
    val visualizationMillis: Long = 240_000L,
    val returnMillis: Long = 120_000L,
    val intention: String? = null,
    val sleepEnding: Boolean = false,
    val voiceGuidance: String = "continuous",
)

object YogaNidraText {
    const val SETTLING = "meditation.yoganidra.settling"
    const val INTENTION = "meditation.yoganidra.intention"
    const val FEET = "meditation.yoganidra.feet"
    const val LEGS = "meditation.yoganidra.legs"
    const val ABDOMEN = "meditation.yoganidra.abdomen"
    const val CHEST = "meditation.yoganidra.chest"
    const val ARMS = "meditation.yoganidra.arms"
    const val HEAD = "meditation.yoganidra.head"
    const val BREATH_AWARENESS = "meditation.yoganidra.breath_awareness"
    const val VISUALIZATION = "meditation.yoganidra.visualization"
    const val RETURN = "meditation.yoganidra.return"
}

fun yogaNidraMeditationDefinition(
    config: YogaNidraConfig = YogaNidraConfig(),
): MeditationDefinition {
    val bodyRegions = listOf(
        "feet" to YogaNidraText.FEET,
        "legs" to YogaNidraText.LEGS,
        "abdomen" to YogaNidraText.ABDOMEN,
        "chest" to YogaNidraText.CHEST,
        "arms" to YogaNidraText.ARMS,
        "head" to YogaNidraText.HEAD,
    )

    val children = listOf(
        cuedPhase(
            id = "settling",
            duration = PhaseDuration.Fixed(config.settlingMillis),
            cueTextId = YogaNidraText.SETTLING,
        ),
        cuedPhase(
            id = "intention",
            duration = PhaseDuration.Fixed(config.intentionMillis),
            cueTextId = YogaNidraText.INTENTION,
        ),
    ) + bodyRegions.map { (region, textId) ->
        restPhase(
            id = "body_$region",
            kind = RestKind.BREATH_AWARENESS,
            duration = PhaseDuration.Fixed(config.bodyRegionMillis),
            cueTextId = textId,
        )
    } + listOf(
        restPhase(
            id = "breath_awareness",
            kind = RestKind.BREATH_AWARENESS,
            duration = PhaseDuration.Fixed(config.breathAwarenessMillis),
            cueTextId = YogaNidraText.BREATH_AWARENESS,
        ),
        cuedPhase(
            id = "visualization",
            duration = PhaseDuration.Fixed(config.visualizationMillis),
            cueTextId = YogaNidraText.VISUALIZATION,
        ),
        cuedPhase(
            id = "return",
            duration = PhaseDuration.Fixed(config.returnMillis),
            cueTextId = YogaNidraText.RETURN,
        ),
    )

    return MeditationDefinition(
        id = "yoganidra",
        variables = mapOf(
            "intention" to config.intention,
            "sleepEnding" to config.sleepEnding,
            "voiceGuidance" to config.voiceGuidance,
        ),
        root = MeditationSequence(id = "yoganidra", children = children),
    )
}
