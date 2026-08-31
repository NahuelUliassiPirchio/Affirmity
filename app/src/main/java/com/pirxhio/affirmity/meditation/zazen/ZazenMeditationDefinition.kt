package com.pirxhio.affirmity.meditation.zazen

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.bellPhase
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Zazen: opening bell, an optional posture cue, long silence, closing bell. Opening and closing
 * bells are two distinct [bellPhase] repeats (distinct ids, since a single `Repeat` node cannot
 * represent two separate strike sequences at different points in the tree). [intervalBellMinutes]
 * (periodic bell strikes during the silence span) has no engine-level effect yet -- a fixed-duration
 * silence [Phase] can't be interrupted mid-span without restructuring the tree into N sub-segments,
 * deferred to a future stage -- recorded in [MeditationDefinition.variables] only.
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

object ZazenAudio {
    const val BELL = "meditation.zazen.bell"
}

fun zazenMeditationDefinition(
    config: ZazenConfig = ZazenConfig(),
): MeditationDefinition {
    val children = buildList {
        add(
            bellPhase(
                id = "opening_bell",
                count = config.openingBellCount,
                audioId = ZazenAudio.BELL,
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
        add(
            restPhase(
                id = "silence",
                kind = RestKind.SILENCE,
                duration = PhaseDuration.Fixed(config.silenceMillis),
            ),
        )
        add(
            bellPhase(
                id = "closing_bell",
                count = config.closingBellCount,
                audioId = ZazenAudio.BELL,
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
