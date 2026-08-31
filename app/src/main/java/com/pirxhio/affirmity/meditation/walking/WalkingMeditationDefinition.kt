package com.pirxhio.affirmity.meditation.walking

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Walking meditation: arrival, standing awareness, mindful walking, closing. [pace] and
 * [guidanceLevel] have no engine-level effect yet -- recorded in [MeditationDefinition.variables]
 * only.
 */
data class WalkingConfig(
    val arrivalMillis: Long = 60_000L,
    val standingAwarenessMillis: Long = 60_000L,
    val walkingMillis: Long = 480_000L,
    val closingMillis: Long = 60_000L,
    val pace: String = "slow",
    val guidanceLevel: String = "full",
)

object WalkingMeditationText {
    const val ARRIVAL = "meditation.walking.arrival"
    const val STANDING = "meditation.walking.standing"
    const val WALKING = "meditation.walking.walking"
    const val CLOSING = "meditation.walking.closing"
}

fun walkingMeditationDefinition(
    config: WalkingConfig = WalkingConfig(),
): MeditationDefinition {
    val children = listOf(
        cuedPhase(
            id = "arrival",
            duration = PhaseDuration.Fixed(config.arrivalMillis),
            cueTextId = WalkingMeditationText.ARRIVAL,
        ),
        restPhase(
            id = "standing_awareness",
            kind = RestKind.OPEN_AWARENESS,
            duration = PhaseDuration.Fixed(config.standingAwarenessMillis),
            cueTextId = WalkingMeditationText.STANDING,
        ),
        cuedPhase(
            id = "walking",
            duration = PhaseDuration.Fixed(config.walkingMillis),
            cueTextId = WalkingMeditationText.WALKING,
        ),
        cuedPhase(
            id = "closing",
            duration = PhaseDuration.Fixed(config.closingMillis),
            cueTextId = WalkingMeditationText.CLOSING,
        ),
    )

    return MeditationDefinition(
        id = "walking",
        variables = mapOf("pace" to config.pace, "guidanceLevel" to config.guidanceLevel),
        root = MeditationSequence(id = "walking", children = children),
    )
}
