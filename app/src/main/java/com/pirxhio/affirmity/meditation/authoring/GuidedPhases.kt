package com.pirxhio.affirmity.meditation.authoring

import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.ShowLiteralText
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.StartAmbient
import com.pirxhio.affirmity.meditation.StopAmbient

/**
 * A single informational span: an optional cue (text and/or voice) held for [duration]. Covers
 * every spec step whose only distinguishing feature is *which* cue plays over a plain timed
 * phase — instructions, guided narration, reflections, prayer, movement cues, duration-bound
 * repetition — see [RestKind] for the sibling case where the phase is a true rest instead.
 */
fun cuedPhase(
    id: String,
    duration: PhaseDuration,
    cueTextId: String? = null,
    cueVoiceId: String? = null,
    skippable: Boolean = false,
): Phase = Phase(
    id = id,
    duration = duration,
    onEnter = listOfNotNull(cueTextId?.let { ShowText(it) }, cueVoiceId?.let { PlayVoice(it) }),
    exitEvents = if (skippable) setOf(MeditationEvent.UserAction::class) else emptySet(),
)

/**
 * A single informational span whose text is not a catalog-authored cue but a runtime string —
 * e.g. one affirmation pulled from the user's own affirmation catalog. No voice-cue parameter:
 * unlike [cuedPhase]'s content, runtime text has no pre-recorded narration to pair it with.
 */
fun literalCuedPhase(
    id: String,
    duration: PhaseDuration,
    literalText: String,
): Phase = Phase(
    id = id,
    duration = duration,
    onEnter = listOf(ShowLiteralText(literalText)),
)

/** A chanted phase: plays [audioId] (e.g. a sung "Om"), optionally preceded by a text cue. */
fun chantPhase(
    id: String,
    duration: PhaseDuration,
    audioId: String,
    cueTextId: String? = null,
): Phase = Phase(
    id = id,
    duration = duration,
    onEnter = listOfNotNull(cueTextId?.let { ShowText(it) }, PlayAudio(audioId)),
)

/**
 * A rapid, non-metronomic breathing span (kapalabhati, breath of fire) driven by an ambient audio
 * loop rather than discrete timed inhale/exhale phases. Unlike [RestKind.AMBIENT], this phase owns
 * its bed's lifecycle end-to-end: it starts [ambientAudioId] on enter and stops it on exit, so it
 * never depends on a preceding `StartAmbient` elsewhere in the tree.
 */
fun rapidBreathCyclePhase(
    id: String,
    duration: PhaseDuration,
    ambientAudioId: String,
    cueTextId: String? = null,
    ambientVolume: Float = 1f,
    fadeOutMillis: Long = 0L,
): Phase = Phase(
    id = id,
    duration = duration,
    onEnter = listOfNotNull(
        cueTextId?.let { ShowText(it) },
        StartAmbient(ambientAudioId, volume = ambientVolume),
    ),
    onExit = listOf(StopAmbient(fadeOutMillis)),
)
