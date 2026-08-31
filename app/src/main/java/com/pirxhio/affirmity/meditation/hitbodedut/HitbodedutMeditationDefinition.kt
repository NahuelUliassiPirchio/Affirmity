package com.pirxhio.affirmity.meditation.hitbodedut

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Hitbodedut: a Jewish Hasidic practice of speaking to God in one's own words — arrival, a
 * reflection span covering one or more prompt topics, spontaneous personal prayer, then closing
 * silence.
 *
 * The reflection span used to be a single fixed "gratitude" phase; it's now built from
 * [HitbodedutConfig.promptTopics] (spec: `promptTopics`, default `gratitude`/`concerns`/`requests`),
 * one [cuedPhase] per selected topic sharing [HitbodedutConfig.reflectionMillis] evenly (remainder
 * folded into the last topic, so the total is always exact). When
 * [HitbodedutConfig.guidedPromptsEnabled] is `false` (spec: `guidedPrompts`), the whole reflection
 * span collapses to one unguided [restPhase] instead — no per-topic cue text, mirroring how
 * `ZazenConfig.openingInstructionsEnabled` toggles a phase in/out.
 */
data class HitbodedutConfig(
    val arrivalMillis: Long = 60_000L,
    val reflectionMillis: Long = 180_000L,
    val personalPrayerMillis: Long = 420_000L,
    val silenceMillis: Long = 120_000L,
    val promptTopics: List<String> = listOf("gratitude", "concerns", "requests"),
    val guidedPromptsEnabled: Boolean = true,
) {
    init {
        require(promptTopics.isNotEmpty()) { "promptTopics must not be empty" }
        require(promptTopics.all { it in CANONICAL_TOPIC_ORDER }) {
            "promptTopics must be a subset of $CANONICAL_TOPIC_ORDER, got $promptTopics"
        }
    }

    companion object {
        /** Canonical spec order; a selection is always rendered in this order regardless of the
         * order the user picked topics in, mirroring [com.pirxhio.affirmity.meditation.metta]'s
         * `targets` precedent. */
        val CANONICAL_TOPIC_ORDER = listOf(
            "gratitude", "concerns", "requests", "relationships", "purpose", "forgiveness",
        )
    }
}

object HitbodedutText {
    const val ARRIVAL = "meditation.hitbodedut.arrival"
    const val GRATITUDE = "meditation.hitbodedut.gratitude"
    const val CONCERNS = "meditation.hitbodedut.concerns"
    const val REQUESTS = "meditation.hitbodedut.requests"
    const val RELATIONSHIPS = "meditation.hitbodedut.relationships"
    const val PURPOSE = "meditation.hitbodedut.purpose"
    const val FORGIVENESS = "meditation.hitbodedut.forgiveness"
    const val PERSONAL_PRAYER = "meditation.hitbodedut.personal_prayer"

    fun forTopic(topic: String): String = when (topic) {
        "gratitude" -> GRATITUDE
        "concerns" -> CONCERNS
        "requests" -> REQUESTS
        "relationships" -> RELATIONSHIPS
        "purpose" -> PURPOSE
        "forgiveness" -> FORGIVENESS
        else -> error("Unknown hitbodedut prompt topic: $topic")
    }
}

fun hitbodedutMeditationDefinition(
    config: HitbodedutConfig = HitbodedutConfig(),
): MeditationDefinition {
    val orderedTopics = HitbodedutConfig.CANONICAL_TOPIC_ORDER.filter { it in config.promptTopics }

    val reflectionPhases = if (!config.guidedPromptsEnabled) {
        listOf(
            restPhase(
                id = "reflection",
                kind = RestKind.SILENCE,
                duration = PhaseDuration.Fixed(config.reflectionMillis),
            ),
        )
    } else {
        val perTopicMillis = config.reflectionMillis / orderedTopics.size
        val remainder = config.reflectionMillis - perTopicMillis * orderedTopics.size
        orderedTopics.mapIndexed { index, topic ->
            val millis = perTopicMillis + if (index == orderedTopics.lastIndex) remainder else 0L
            cuedPhase(
                id = "reflection_$topic",
                duration = PhaseDuration.Fixed(millis),
                cueTextId = HitbodedutText.forTopic(topic),
            )
        }
    }

    val children = listOf(
        cuedPhase(
            id = "arrival",
            duration = PhaseDuration.Fixed(config.arrivalMillis),
            cueTextId = HitbodedutText.ARRIVAL,
        ),
    ) + reflectionPhases + listOf(
        cuedPhase(
            id = "personal_prayer",
            duration = PhaseDuration.Fixed(config.personalPrayerMillis),
            cueTextId = HitbodedutText.PERSONAL_PRAYER,
        ),
        restPhase(
            id = "silence",
            kind = RestKind.SILENCE,
            duration = PhaseDuration.Fixed(config.silenceMillis),
        ),
    )

    return MeditationDefinition(
        id = "hitbodedut",
        root = MeditationSequence(id = "hitbodedut", children = children),
    )
}
