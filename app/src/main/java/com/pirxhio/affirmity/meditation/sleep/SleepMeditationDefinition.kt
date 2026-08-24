package com.pirxhio.affirmity.meditation.sleep

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.StartAmbient
import com.pirxhio.affirmity.meditation.StopAmbient
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.breathingBlock
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * "Dormir 10min" — catalog entry `dormir` (REQ-4.11.4, design §7.4-7.5). `ProOrAdTrial`,
 * content-rich; ends with **no gong, with a fade-out** (AR-3). A NEW, independently-authored
 * definition — deliberately does NOT reuse or scale up
 * [com.pirxhio.affirmity.meditation.winddown.windDownMeditationDefinition] (§3 non-goals; that
 * file stays a Spec-2-owned validation fixture, byte-for-byte untouched).
 *
 * `30 + 60 + 144 + 150 + 60 + 150 + 5.5 = 599.5s`, declared 10 min (500ms under `EXACT_TOLERANCE`).
 * No [com.pirxhio.affirmity.meditation.PlayAudio] anywhere -> no gong. `sleep_fadeout`'s 5 000ms
 * fade sits on `onEnter` of a 5 500ms `Fixed` phase -> AR-3 satisfied structurally.
 */
object SleepText {
    const val SETTLE = "meditation.sleep.settle"
    const val GUIDANCE = "meditation.sleep.guidance"
    const val REST_1 = "meditation.sleep.rest_1"
    const val RELEASE = "meditation.sleep.release"
    const val REST_2 = "meditation.sleep.rest_2"
    const val CLOSING = "meditation.sleep.closing"
}

object SleepAudio {
    const val BED = "meditation.sleep.bed"
    const val INTRO = "meditation.sleep.intro"
    const val RELEASE = "meditation.sleep.release"
}

fun sleepMeditationDefinition(): MeditationDefinition = MeditationDefinition(
    id = "sleep",
    root = MeditationSequence(
        id = "sleep",
        children = listOf(
            Phase(
                id = "sleep_settle",
                duration = PhaseDuration.Fixed(30_000L),
                onEnter = listOf(
                    StartAmbient(SleepAudio.BED, volume = 0.6f, fadeInMillis = 6_000L),
                    ShowText(SleepText.SETTLE),
                ),
            ),
            Phase(
                id = "sleep_guidance",
                duration = PhaseDuration.Fixed(60_000L),
                onEnter = listOf(
                    ShowText(SleepText.GUIDANCE),
                    // Un-gated PlayVoice (AR-1): no exitEvents, the authored Fixed span carries the
                    // pacing whether or not a real asset is mapped (audioResources stays empty, §7.3).
                    PlayVoice(SleepAudio.INTRO),
                ),
            ),
            breathingBlock(
                id = "sleep_breathing",
                breaths = 12,
                inhaleMillis = 4_000L,
                exhaleMillis = 8_000L,
                breathId = "sleep_breath",
                inhaleId = "sleep_inhale",
                exhaleId = "sleep_exhale",
            ),
            // AMBIENT is correct for both rest phases: the bed started in sleep_settle is still
            // playing, so neither rest phase emits an audio command of its own.
            restPhase(
                id = "sleep_rest_1",
                kind = RestKind.AMBIENT,
                duration = PhaseDuration.Fixed(150_000L),
                cueTextId = SleepText.REST_1,
            ),
            Phase(
                id = "sleep_release",
                duration = PhaseDuration.Fixed(60_000L),
                onEnter = listOf(
                    ShowText(SleepText.RELEASE),
                    PlayVoice(SleepAudio.RELEASE), // un-gated, AR-1
                ),
            ),
            restPhase(
                id = "sleep_rest_2",
                kind = RestKind.AMBIENT,
                duration = PhaseDuration.Fixed(150_000L),
                cueTextId = SleepText.REST_2,
            ),
            Phase(
                id = "sleep_fadeout",
                // AR-3: the 5 000ms StopAmbient fade sits on onEnter of a Fixed(5 500ms) phase — the
                // fade is strictly shorter than the phase that authors it, never on onExit.
                duration = PhaseDuration.Fixed(5_500L),
                onEnter = listOf(
                    ShowText(SleepText.CLOSING),
                    StopAmbient(fadeOutMillis = 5_000L),
                ),
            ),
        ),
    ),
)
