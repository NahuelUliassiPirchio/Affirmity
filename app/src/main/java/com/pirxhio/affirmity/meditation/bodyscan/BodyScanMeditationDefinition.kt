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
 * `20 + 60 + (6 x 80) + 40 = 600s` = 10 min exact.
 */
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

fun bodyScanMeditationDefinition(): MeditationDefinition = MeditationDefinition(
    id = "bodyscan",
    root = MeditationSequence(
        id = "bodyscan",
        children = listOf(
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
            restPhase(
                id = "bs_feet",
                kind = RestKind.BREATH_AWARENESS,
                duration = PhaseDuration.Fixed(80_000L),
                cueTextId = BodyScanText.FEET,
            ),
            restPhase(
                id = "bs_legs",
                kind = RestKind.BREATH_AWARENESS,
                duration = PhaseDuration.Fixed(80_000L),
                cueTextId = BodyScanText.LEGS,
            ),
            restPhase(
                id = "bs_torso",
                kind = RestKind.BREATH_AWARENESS,
                duration = PhaseDuration.Fixed(80_000L),
                cueTextId = BodyScanText.TORSO,
            ),
            restPhase(
                id = "bs_arms",
                kind = RestKind.BREATH_AWARENESS,
                duration = PhaseDuration.Fixed(80_000L),
                cueTextId = BodyScanText.ARMS,
            ),
            restPhase(
                id = "bs_head",
                kind = RestKind.BREATH_AWARENESS,
                duration = PhaseDuration.Fixed(80_000L),
                cueTextId = BodyScanText.HEAD,
            ),
            restPhase(
                id = "bs_whole",
                kind = RestKind.OPEN_AWARENESS,
                duration = PhaseDuration.Fixed(80_000L),
                cueTextId = BodyScanText.WHOLE,
            ),
            Phase(
                id = "bs_close",
                duration = PhaseDuration.Fixed(40_000L),
                onEnter = listOf(ShowText(BodyScanText.CLOSING)),
            ),
        ),
    ),
)
