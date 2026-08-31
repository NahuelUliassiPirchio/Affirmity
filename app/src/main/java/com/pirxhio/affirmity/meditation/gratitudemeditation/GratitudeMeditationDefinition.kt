package com.pirxhio.affirmity.meditation.gratitudemeditation

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.cuedPhase

/**
 * Gratitude Meditation: an arrival cue, then three reflection prompts (person, experience,
 * present-moment). Each spec `reflection` step carries its own `prompt`, so each gets a distinct
 * [cuedPhase] cue rather than sharing one generic reflection cue.
 */
data class GratitudeConfig(
    val arrivalMillis: Long = 60_000L,
    val personMillis: Long = 120_000L,
    val experienceMillis: Long = 120_000L,
    val presentMillis: Long = 120_000L,
    /** Only the first [promptCount] prompts (person, experience, present, in that canonical
     * order) run. Range 1-3, not the spec's 1-5 -- only 3 prompts exist in this structure. */
    val promptCount: Int = 3,
    /** Whether to prompt a post-session journal entry -- no journaling feature exists yet, so
     * this has no engine effect yet; recorded in [MeditationDefinition.variables] only. */
    val journalAtEnd: Boolean = false,
) {
    init {
        require(promptCount in 1..3) { "promptCount must be in 1..3, got $promptCount" }
    }
}

object GratitudeMeditationText {
    const val ARRIVAL = "meditation.gratitudemeditation.arrival"
    const val PERSON = "meditation.gratitudemeditation.person"
    const val EXPERIENCE = "meditation.gratitudemeditation.experience"
    const val PRESENT = "meditation.gratitudemeditation.present"
}

fun gratitudeMeditationDefinition(
    config: GratitudeConfig = GratitudeConfig(),
): MeditationDefinition {
    val children = buildList {
        add(
            cuedPhase(
                id = "arrival",
                duration = PhaseDuration.Fixed(config.arrivalMillis),
                cueTextId = GratitudeMeditationText.ARRIVAL,
            ),
        )
        if (config.promptCount >= 1) {
            add(
                cuedPhase(
                    id = "person",
                    duration = PhaseDuration.Fixed(config.personMillis),
                    cueTextId = GratitudeMeditationText.PERSON,
                ),
            )
        }
        if (config.promptCount >= 2) {
            add(
                cuedPhase(
                    id = "experience",
                    duration = PhaseDuration.Fixed(config.experienceMillis),
                    cueTextId = GratitudeMeditationText.EXPERIENCE,
                ),
            )
        }
        if (config.promptCount >= 3) {
            add(
                cuedPhase(
                    id = "present",
                    duration = PhaseDuration.Fixed(config.presentMillis),
                    cueTextId = GratitudeMeditationText.PRESENT,
                ),
            )
        }
    }

    return MeditationDefinition(
        id = "gratitudemeditation",
        variables = mapOf("journalAtEnd" to config.journalAtEnd),
        root = MeditationSequence(id = "gratitudemeditation", children = children),
    )
}
