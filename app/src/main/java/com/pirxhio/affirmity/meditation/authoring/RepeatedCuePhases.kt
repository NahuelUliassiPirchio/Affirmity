package com.pirxhio.affirmity.meditation.authoring

import com.pirxhio.affirmity.meditation.FixedCountRepetition
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.PlayCue
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.audio.MeditationCue

const val DEFAULT_BELL_STRIKE_MILLIS = 2_000L

/** [count] strikes of [audioId] (silent when null -- the phase still paces the strike), each held for [strikeDurationMillis] (zazen's opening/closing
 * bells). */
fun bellPhase(
    id: String,
    count: Int,
    audioId: String?,
    strikeDurationMillis: Long = DEFAULT_BELL_STRIKE_MILLIS,
    strikeId: String = "strike",
): Repeat {
    require(count > 0) { "count must be > 0, got $count" }
    return Repeat(
        id = id,
        child = Phase(
            id = strikeId,
            duration = PhaseDuration.Fixed(strikeDurationMillis),
            onEnter = listOfNotNull(audioId?.let { PlayAudio(it) }),
        ),
        strategy = FixedCountRepetition(count),
    )
}

/** [count] repetitions of a cued phrase (dhikr's 33/99 repetitions), each held for
 * [repetitionDurationMillis]. Distinct from [bellPhase]: the cue here is a spoken/shown phrase,
 * not a struck sound. */
fun countedRepetitionPhase(
    id: String,
    count: Int,
    repetitionDurationMillis: Long,
    cueTextId: String? = null,
    cueVoiceId: String? = null,
    repetitionId: String = "repetition",
    /** Rung once, on the first repetition only (via `onlyAtFirstIterationOf`) -- never per repetition. */
    entryCue: MeditationCue? = null,
): Repeat {
    require(count > 0) { "count must be > 0, got $count" }
    return Repeat(
        id = id,
        child = Phase(
            id = repetitionId,
            duration = PhaseDuration.Fixed(repetitionDurationMillis),
            onEnter = listOfNotNull(
                entryCue?.let { PlayCue(it, onlyAtFirstIterationOf = id) },
                cueTextId?.let { ShowText(it) },
                cueVoiceId?.let { PlayVoice(it) },
            ),
        ),
        strategy = FixedCountRepetition(count),
    )
}
