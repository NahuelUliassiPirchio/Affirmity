package com.pirxhio.affirmity.meditation.calm

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.breathingBlock
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * "Calma 5min" — catalog entry `calma` (REQ-4.11.2, design §7.5). Free, simple. First consumer of
 * [breathingBlock]'s `holdAfterInhale` parameter.
 *
 * `15 + 240 + 30 + 15 = 300s` = 5 min exact.
 */
object CalmText {
    const val INTRO = "meditation.calm.intro"
    const val HOLD = "meditation.calm.hold"
    const val REST = "meditation.calm.rest"
    const val CLOSING = "meditation.calm.closing"
}

fun calmMeditationDefinition(): MeditationDefinition = MeditationDefinition(
    id = "calm",
    root = MeditationSequence(
        id = "calm",
        children = listOf(
            Phase(
                id = "calm_intro",
                duration = PhaseDuration.Fixed(15_000L),
                onEnter = listOf(ShowText(CalmText.INTRO)),
            ),
            breathingBlock(
                id = "calm_breathing",
                breaths = 20,
                inhaleMillis = 4_000L,
                holdAfterInhaleMillis = 2_000L,
                exhaleMillis = 6_000L,
                holdTextId = CalmText.HOLD,
                breathId = "calm_breath",
                inhaleId = "calm_inhale",
                exhaleId = "calm_exhale",
                holdAfterInhaleId = "calm_hold",
            ),
            restPhase(
                id = "calm_rest",
                kind = RestKind.BREATH_AWARENESS,
                duration = PhaseDuration.Fixed(30_000L),
                cueTextId = CalmText.REST,
            ),
            Phase(
                id = "calm_close",
                duration = PhaseDuration.Fixed(15_000L),
                onEnter = listOf(ShowText(CalmText.CLOSING)),
            ),
        ),
    ),
)
