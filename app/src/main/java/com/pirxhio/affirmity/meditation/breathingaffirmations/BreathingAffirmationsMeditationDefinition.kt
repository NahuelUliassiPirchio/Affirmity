package com.pirxhio.affirmity.meditation.breathingaffirmations

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.breathingBlock
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.literalCuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase
import com.pirxhio.affirmity.meditation.boxbreathing.BoxBreathingText

/**
 * Breathe & Affirm: a technique-selectable breathing block, a breath-awareness span, one
 * [literalCuedPhase] per fetched affirmation, then a closing silence. The only meditation in this
 * catalog whose content is not fully authored at catalog-build time -- [affirmationTexts] is
 * fetched asynchronously from the user's own affirmation catalog by the caller
 * (`ui/meditation/customization/BreathingAffirmationsAffirmationSource.kt`) before this builder
 * ever runs. This file stays Android-free and plain-JUnit testable, exactly like every other
 * definition in this package: it never talks to Room/repositories itself, only receives resolved
 * strings.
 */
data class BreathingAffirmationsConfig(
    val breathingTechnique: String = "coherent_breathing",
    val breathingMillis: Long = 180_000L,
    val meditationMillis: Long = 300_000L,
    val affirmationMillis: Long = 120_000L,
    /** Fetched content. Empty means "not loaded yet" or "nothing available" -- handled with a
     * single fallback cue rather than a zero-phase sequence, never a crash. */
    val affirmationTexts: List<String> = emptyList(),
)

object BreathingAffirmationsText {
    const val MEDITATION = "meditation.breathingaffirmations.meditation"
    const val AFFIRMATION_UNAVAILABLE = "meditation.breathingaffirmations.affirmation_unavailable"
}

/** One breath cycle's length per [BreathingAffirmationsConfig.breathingTechnique], used to derive
 * a breath count from [BreathingAffirmationsConfig.breathingMillis] -- this meditation is
 * duration-first (spec: `breathingMinutes`), not rounds-first, matching `coherent_breathing`'s own
 * framing. */
private fun breathingPhaseFor(technique: String, breathingMillis: Long) = when (technique) {
    "extended_exhale" -> breathingBlock(
        id = "breathing",
        breaths = (breathingMillis / 10_000L).toInt().coerceAtLeast(1),
        inhaleMillis = 4_000L,
        exhaleMillis = 6_000L,
    )
    "box_breathing" -> breathingBlock(
        id = "breathing",
        breaths = (breathingMillis / 16_000L).toInt().coerceAtLeast(1),
        inhaleMillis = 4_000L,
        exhaleMillis = 4_000L,
        holdAfterInhaleMillis = 4_000L,
        holdAfterExhaleMillis = 4_000L,
        holdTextId = BoxBreathingText.HOLD,
    )
    // "coherent_breathing" and any unrecognized value: the spec's own default technique.
    else -> breathingBlock(
        id = "breathing",
        breaths = (breathingMillis / 10_000L).toInt().coerceAtLeast(1),
        inhaleMillis = 5_000L,
        exhaleMillis = 5_000L,
    )
}

fun breathingAffirmationsMeditationDefinition(
    config: BreathingAffirmationsConfig = BreathingAffirmationsConfig(),
): MeditationDefinition {
    require(config.breathingMillis > 0) { "breathingMillis must be > 0, got ${config.breathingMillis}" }
    require(config.meditationMillis > 0) { "meditationMillis must be > 0, got ${config.meditationMillis}" }
    require(config.affirmationMillis > 0) { "affirmationMillis must be > 0, got ${config.affirmationMillis}" }

    val affirmationsNode = if (config.affirmationTexts.isNotEmpty()) {
        val perAffirmationMillis = config.affirmationMillis / config.affirmationTexts.size
        MeditationSequence(
            id = "affirmations",
            children = config.affirmationTexts.mapIndexed { index, text ->
                literalCuedPhase(
                    id = "affirmation_$index",
                    duration = PhaseDuration.Fixed(perAffirmationMillis),
                    literalText = text,
                )
            },
        )
    } else {
        cuedPhase(
            id = "affirmations",
            duration = PhaseDuration.Fixed(config.affirmationMillis),
            cueTextId = BreathingAffirmationsText.AFFIRMATION_UNAVAILABLE,
        )
    }

    val children = listOf(
        breathingPhaseFor(config.breathingTechnique, config.breathingMillis),
        restPhase(
            id = "meditation",
            kind = RestKind.BREATH_AWARENESS,
            duration = PhaseDuration.Fixed(config.meditationMillis),
            cueTextId = BreathingAffirmationsText.MEDITATION,
        ),
        affirmationsNode,
        restPhase(
            id = "silence",
            kind = RestKind.SILENCE,
            duration = PhaseDuration.Fixed(60_000L),
        ),
    )

    return MeditationDefinition(
        id = "breathingaffirmations",
        variables = mapOf(
            "breathingTechnique" to config.breathingTechnique,
            "affirmationCount" to config.affirmationTexts.size,
        ),
        root = MeditationSequence(id = "breathingaffirmations", children = children),
    )
}
