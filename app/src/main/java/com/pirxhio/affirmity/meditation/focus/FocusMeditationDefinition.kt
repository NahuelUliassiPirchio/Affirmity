package com.pirxhio.affirmity.meditation.focus

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.breathingBlock
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * "Enfoque 10min" — catalog entry `enfoque` (REQ-4.11.3, design §7.5). `ProOrAdPerUse`, simple —
 * its value in this spec is the access path (`PER_USE`), not content richness. Box breathing
 * (both `breathingBlock` hold parameters).
 *
 * `20 + 360 + 180 + 40 = 600s` = 10 min exact.
 */
object FocusText {
    const val INTRO = "meditation.focus.intro"
    const val HOLD = "meditation.focus.hold"
    const val ANCHOR = "meditation.focus.anchor"
    const val CLOSING = "meditation.focus.closing"
}

fun focusMeditationDefinition(): MeditationDefinition = MeditationDefinition(
    id = "focus",
    root = MeditationSequence(
        id = "focus",
        children = listOf(
            Phase(
                id = "focus_intro",
                duration = PhaseDuration.Fixed(20_000L),
                onEnter = listOf(ShowText(FocusText.INTRO)),
            ),
            breathingBlock(
                id = "focus_breathing",
                breaths = 30,
                inhaleMillis = 4_000L,
                holdAfterInhaleMillis = 2_000L,
                exhaleMillis = 4_000L,
                holdAfterExhaleMillis = 2_000L,
                holdTextId = FocusText.HOLD,
                breathId = "focus_breath",
                inhaleId = "focus_inhale",
                exhaleId = "focus_exhale",
                holdAfterInhaleId = "focus_hold_in",
                holdAfterExhaleId = "focus_hold_out",
            ),
            restPhase(
                id = "focus_anchor",
                kind = RestKind.OPEN_AWARENESS,
                duration = PhaseDuration.Fixed(180_000L),
                cueTextId = FocusText.ANCHOR,
            ),
            Phase(
                id = "focus_close",
                duration = PhaseDuration.Fixed(40_000L),
                onEnter = listOf(ShowText(FocusText.CLOSING)),
            ),
        ),
    ),
)
