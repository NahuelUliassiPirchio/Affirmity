package com.pirxhio.affirmity.meditation.anapanasati

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Mindful breathing (anapanasati): arrival, a long breath-awareness span, closing. `durationMinutes`
 * drives [awarenessMillis] at the catalog layer (arrival/closing stay fixed). [guidanceLevel] and
 * [reminderIntervalMinutes] are recorded in [MeditationDefinition.variables] only — the engine has
 * no concept of periodic reminders or a lighter/silent cue variant yet, so they have no behavioral
 * effect until a future stage adds one.
 */
data class AnapanasatiConfig(
    val arrivalMillis: Long = 60_000L,
    val awarenessMillis: Long = 480_000L,
    val closingMillis: Long = 60_000L,
    val guidanceLevel: String = "full",
    val reminderIntervalMinutes: Int = 2,
)

object AnapanasatiText {
    const val ARRIVAL = "meditation.anapanasati.arrival"
    const val AWARENESS = "meditation.anapanasati.awareness"
    const val CLOSING = "meditation.anapanasati.closing"
}

fun anapanasatiMeditationDefinition(
    config: AnapanasatiConfig = AnapanasatiConfig(),
): MeditationDefinition {
    val children = listOf(
        cuedPhase(
            id = "arrival",
            duration = PhaseDuration.Fixed(config.arrivalMillis),
            cueTextId = AnapanasatiText.ARRIVAL,
        ),
        restPhase(
            id = "breath_awareness",
            kind = RestKind.BREATH_AWARENESS,
            duration = PhaseDuration.Fixed(config.awarenessMillis),
            cueTextId = AnapanasatiText.AWARENESS,
        ),
        cuedPhase(
            id = "closing",
            duration = PhaseDuration.Fixed(config.closingMillis),
            cueTextId = AnapanasatiText.CLOSING,
        ),
    )

    return MeditationDefinition(
        id = "anapanasati",
        variables = mapOf(
            "guidanceLevel" to config.guidanceLevel,
            "reminderIntervalMinutes" to config.reminderIntervalMinutes,
        ),
        root = MeditationSequence(id = "anapanasati", children = children),
    )
}
