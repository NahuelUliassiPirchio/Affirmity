package com.pirxhio.affirmity.meditation.jesusprayer

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.authoring.breathingBlock

/**
 * Jesus Prayer: inhale carries "Lord Jesus Christ, Son of God", exhale carries "have mercy on
 * me". Same shape as `breath_mantra` (so-hum) and `breath_prayer` — Stage 1/1b confirmed this
 * needs no new primitive, just [breathingBlock] with the phrases as the inhale/exhale text cues.
 * The spec gives no per-phase duration, only a 10-minute default total; [breaths] (67) mirrors
 * so-hum's resolution for the same shape of gap: an estimate that lands close to 10 minutes at a
 * natural ~9s/breath pace (5s inhale + 4s exhale), not a claim about traditional pacing.
 */
data class JesusPrayerConfig(
    val breaths: Int = 67,
    val inhaleMillis: Long = 5_000L,
    val exhaleMillis: Long = 4_000L,
    /** Whether the repetition is synced to the breath and how it's voiced -- the definition is
     * inherently a [breathingBlock] already paced by inhale/exhale, and the repetition text is
     * fixed regardless of voicing mode, so these have no additional engine effect yet; recorded
     * in [MeditationDefinition.variables] only. */
    val syncWithBreath: Boolean = true,
    val repetitionMode: String = "mental",
)

object JesusPrayerText {
    const val INHALE = "meditation.jesusprayer.inhale"
    const val EXHALE = "meditation.jesusprayer.exhale"
}

fun jesusPrayerMeditationDefinition(
    config: JesusPrayerConfig = JesusPrayerConfig(),
): MeditationDefinition {
    require(config.breaths > 0) { "breaths must be > 0, got ${config.breaths}" }

    return MeditationDefinition(
        id = "jesusprayer",
        variables = mapOf(
            "breaths" to config.breaths,
            "syncWithBreath" to config.syncWithBreath,
            "repetitionMode" to config.repetitionMode,
        ),
        root = breathingBlock(
            id = "breathing",
            breaths = config.breaths,
            inhaleMillis = config.inhaleMillis,
            exhaleMillis = config.exhaleMillis,
            inhaleTextId = JesusPrayerText.INHALE,
            exhaleTextId = JesusPrayerText.EXHALE,
        ),
    )
}
