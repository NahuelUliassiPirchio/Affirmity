package com.pirxhio.affirmity.meditation.winddown

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.StartAmbient
import com.pirxhio.affirmity.meditation.StopAmbient
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Validation definition (design §8) — a ~90-second miniature of Spec 3's "Dormir 10min"
 * requirement (ends with **no gong, with a fade-out**). Deliberately a new small sibling
 * definition, NOT an extension of [com.pirxhio.affirmity.meditation.breathing.
 * breathingMeditationDefinition]: that demo is characterised by nearly every assertion in
 * `MeditationEngineTest`, so appending a closing ambient sequence there would force churn across
 * roughly half that file (design §8).
 *
 * Exercises every new vocabulary element end to end, proven in [WindDownMeditationDefinitionTest]:
 * - `settle` — [StartAmbient] with looping (default) and a fade-in.
 * - `guidance` — [PlayVoice] with ducking (default params), exiting on [MeditationEvent.
 *   AudioCompleted] (a real phase-exit trigger) or a 20s ceiling, whichever comes first.
 * - `rest` — [RestKind.AMBIENT]: no audio command of its own, the `settle` bed is still playing.
 * - `fadeout` — closing cue plus [StopAmbient], authored on `onEnter` of a phase long enough to
 *   contain the fade (design §5's single most important authoring rule: never author a terminal
 *   fade as the literal last thing before an expected navigation).
 *
 * The session therefore ends in silence with no [com.pirxhio.affirmity.meditation.PlayAudio]/gong
 * command dispatched anywhere in the whole run.
 */
object WindDownAudio {
    /** Both map to `R.raw.meditation_gong` at the UI wiring layer (the only asset that ships
     * today), looped, purely to exercise looping/ramping mechanics — not a content decision.
     * Sourcing real ambient/voice assets is Spec 3's job (accepted design assumption (e)). This
     * definition itself carries no resource mapping — that is the executor's `resourcesByAudioId`,
     * supplied wherever a screen actually plays it. */
    const val BED = "meditation.winddown.bed"
    const val INTRO = "meditation.winddown.intro"
}

/** Text ids the domain emits via [ShowText] — the UI layer maps these to localized strings. */
object WindDownText {
    const val SETTLE = "meditation.winddown.settle"
    const val GUIDANCE = "meditation.winddown.guidance"
    const val REST = "meditation.winddown.rest"
    const val CLOSING = "meditation.winddown.closing"
}

fun windDownMeditationDefinition(): MeditationDefinition = MeditationDefinition(
    id = "winddown",
    root = MeditationSequence(
        id = "winddown",
        children = listOf(
            Phase(
                id = "settle",
                duration = PhaseDuration.Fixed(15_000L),
                onEnter = listOf(
                    StartAmbient(WindDownAudio.BED, volume = 0.6f, fadeInMillis = 4_000L),
                    ShowText(WindDownText.SETTLE),
                ),
            ),
            Phase(
                id = "guidance",
                duration = PhaseDuration.Fixed(20_000L),
                onEnter = listOf(
                    ShowText(WindDownText.GUIDANCE),
                    PlayVoice(WindDownAudio.INTRO),
                ),
                exitEvents = setOf(MeditationEvent.AudioCompleted::class),
            ),
            restPhase(
                id = "rest",
                kind = RestKind.AMBIENT,
                duration = PhaseDuration.Fixed(45_000L),
                cueTextId = WindDownText.REST,
            ),
            Phase(
                id = "fadeout",
                duration = PhaseDuration.Fixed(5_500L),
                onEnter = listOf(
                    ShowText(WindDownText.CLOSING),
                    StopAmbient(fadeOutMillis = 5_000L),
                ),
            ),
        ),
    ),
)
