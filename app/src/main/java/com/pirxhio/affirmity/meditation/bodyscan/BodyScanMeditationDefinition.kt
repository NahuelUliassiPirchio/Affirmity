package com.pirxhio.affirmity.meditation.bodyscan

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.breathingBlock
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * "Body Scan 10min" — catalog entry `body_scan` (REQ-4.11.5, design §7.5). Pro, simple. The
 * clearest demonstration that [RestKind]'s four values are an editorial vocabulary, not a runtime
 * one: six rest spans of identical shape, differing only in cue text and (for the last) `RestKind`.
 *
 * `20 + 60 + (6 x 80) + 40 = 600s` = 10 min exact (default [BodyScanConfig]).
 *
 * [BodyScanConfig.direction] genuinely reverses the five-region traversal order (feet-to-head vs
 * head-to-feet); [BodyScanConfig.detailLevel] has no defined region-count mapping yet, so it is
 * only recorded in [MeditationDefinition.variables] (body-awareness customization).
 */
data class BodyScanConfig(
    val perRegionMillis: Long = 80_000L,
    val direction: String = "feet_to_head",
    val detailLevel: String = "standard",
)

object BodyScanText {
    const val INTRO = "meditation.bodyscan.intro"
    const val FEET = "meditation.bodyscan.feet"
    const val LEGS = "meditation.bodyscan.legs"
    const val TORSO = "meditation.bodyscan.torso"
    const val ARMS = "meditation.bodyscan.arms"
    const val HEAD = "meditation.bodyscan.head"
    const val WHOLE = "meditation.bodyscan.whole"
    const val CLOSING = "meditation.bodyscan.closing"
}

fun bodyScanMeditationDefinition(config: BodyScanConfig = BodyScanConfig()): MeditationDefinition {
    val regions = listOf(
        "bs_feet" to BodyScanText.FEET,
        "bs_legs" to BodyScanText.LEGS,
        "bs_torso" to BodyScanText.TORSO,
        "bs_arms" to BodyScanText.ARMS,
        "bs_head" to BodyScanText.HEAD,
    )
    val orderedRegions = if (config.direction == "head_to_feet") regions.reversed() else regions

    val children = listOf(
        Phase(
            id = "bs_intro",
            duration = PhaseDuration.Fixed(20_000L),
            onEnter = listOf(ShowText(BodyScanText.INTRO)),
        ),
        breathingBlock(
            id = "bs_breathing",
            breaths = 6,
            inhaleMillis = 4_000L,
            exhaleMillis = 6_000L,
            breathId = "bs_breath",
            inhaleId = "bs_inhale",
            exhaleId = "bs_exhale",
        ),
    ) + orderedRegions.map { (id, textId) ->
        restPhase(
            id = id,
            kind = RestKind.BREATH_AWARENESS,
            duration = PhaseDuration.Fixed(config.perRegionMillis),
            cueTextId = textId,
        )
    } + listOf(
        restPhase(
            id = "bs_whole",
            kind = RestKind.OPEN_AWARENESS,
            duration = PhaseDuration.Fixed(config.perRegionMillis),
            cueTextId = BodyScanText.WHOLE,
        ),
        Phase(
            id = "bs_close",
            duration = PhaseDuration.Fixed(40_000L),
            onEnter = listOf(ShowText(BodyScanText.CLOSING)),
        ),
    )

    return MeditationDefinition(
        id = "bodyscan",
        variables = mapOf("direction" to config.direction, "detailLevel" to config.detailLevel),
        root = MeditationSequence(id = "bodyscan", children = children),
    )
}
