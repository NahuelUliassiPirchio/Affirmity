package com.pirxhio.affirmity.meditation.zazen

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.audio.MeditationCue
import com.pirxhio.affirmity.meditation.authoring.withEntryCue
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.bellPhase
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Zazen: opening strikes, an optional posture cue, long silence, closing strikes. The opening and
 * closing sound comes from the engine's global SessionStart/SessionEnd cues, so the strike phases
 * are kept (distinct [bellPhase] repeats, for their pacing and counters) but carry no audio of
 * their own -- otherwise the bells would double.
 *
 * [intervalBellMinutes] > 0 splits the silence into segments that sum exactly to [silenceMillis],
 * ringing a ZazenBell cue at each interval boundary strictly inside the span (never at its start
 * or end). 0, or an interval at or beyond the silence span, leaves a single silent phase.
 */
data class ZazenConfig(
    val openingBellCount: Int = 3,
    val closingBellCount: Int = 3,
    val postureMillis: Long = 60_000L,
    val silenceMillis: Long = 540_000L,
    val openingInstructionsEnabled: Boolean = true,
    val intervalBellMinutes: Int = 0,
)

object ZazenText {
    const val POSTURE = "meditation.zazen.posture"
}

fun zazenMeditationDefinition(
    config: ZazenConfig = ZazenConfig(),
): MeditationDefinition {
    val children = buildList {
        add(
            bellPhase(
                id = "opening_bell",
                count = config.openingBellCount,
                audioId = null,
                strikeId = "opening_strike",
            ),
        )
        if (config.openingInstructionsEnabled) {
            add(
                cuedPhase(
                    id = "posture",
                    duration = PhaseDuration.Fixed(config.postureMillis),
                    cueTextId = ZazenText.POSTURE,
                ),
            )
        }
        addAll(silenceSegments(config))
        add(
            bellPhase(
                id = "closing_bell",
                count = config.closingBellCount,
                audioId = null,
                strikeId = "closing_strike",
            ),
        )
    }

    return MeditationDefinition(
        id = "zazen",
        variables = mapOf("intervalBellMinutes" to config.intervalBellMinutes),
        root = MeditationSequence(id = "zazen", children = children),
    )
}

private fun silenceSegments(config: ZazenConfig): List<Phase> {
    val intervalMillis = config.intervalBellMinutes * 60_000L
    val ringsInside = intervalMillis > 0L && intervalMillis < config.silenceMillis
    if (!ringsInside) return listOf(silencePhase("silence", config.silenceMillis))

    val segments = mutableListOf<Phase>()
    var remaining = config.silenceMillis
    var index = 1
    while (remaining > 0L) {
        val millis = minOf(intervalMillis, remaining)
        val id = if (index == 1) "silence" else "silence_$index"
        val phase = silencePhase(id, millis)
        segments += if (index == 1) phase else phase.withEntryCue(MeditationCue.ZazenBell)
        remaining -= millis
        index++
    }
    return segments
}

private fun silencePhase(id: String, millis: Long): Phase =
    restPhase(id = id, kind = RestKind.SILENCE, duration = PhaseDuration.Fixed(millis))
