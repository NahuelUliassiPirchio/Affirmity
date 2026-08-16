package com.pirxhio.affirmity.meditation.quickreset

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.breathingBlock
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * "Reset rápido 2min" — catalog entry `reset_rapido` (REQ-4.11.1, design §7.5). Free, content-rich:
 * an un-gated [PlayVoice] cue (AR-1) plus 8 breaths of 4s inhale / 6s exhale.
 *
 * `10 + 80 + 20 + 10 = 120s` = 2 min exact.
 */
object QuickResetText {
    const val INTRO = "meditation.quickreset.intro"
    const val CLOSING = "meditation.quickreset.closing"
}

object QuickResetAudio {
    const val INTRO = "meditation.quickreset.intro"
}

fun quickResetMeditationDefinition(): MeditationDefinition = MeditationDefinition(
    id = "quickreset",
    root = MeditationSequence(
        id = "quickreset",
        children = listOf(
            Phase(
                id = "qr_intro",
                duration = PhaseDuration.Fixed(10_000L),
                onEnter = listOf(
                    ShowText(QuickResetText.INTRO),
                    PlayVoice(QuickResetAudio.INTRO),
                ),
            ),
            breathingBlock(
                id = "qr_breathing",
                breaths = 8,
                inhaleMillis = 4_000L,
                exhaleMillis = 6_000L,
                breathId = "qr_breath",
                inhaleId = "qr_inhale",
                exhaleId = "qr_exhale",
            ),
            restPhase(
                id = "qr_settle",
                kind = RestKind.SILENCE,
                duration = PhaseDuration.Fixed(20_000L),
            ),
            Phase(
                id = "qr_close",
                duration = PhaseDuration.Fixed(10_000L),
                onEnter = listOf(ShowText(QuickResetText.CLOSING)),
            ),
        ),
    ),
)
