package com.pirxhio.affirmity.meditation.centeringprayer

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * Centering Prayer: choosing a sacred word, an extended silence, then a closing prayer. The
 * silence phase carries no cue — `RestKind.SILENCE` rejects one outright, matching how the
 * existing dhikr/centering silence spans in this catalog are modelled.
 */
data class CenteringPrayerConfig(
    val sacredWordMillis: Long = 60_000L,
    val silenceMillis: Long = 1_080_000L,
    val closingMillis: Long = 60_000L,
    val includeSacredWord: Boolean = true,
    /** User-chosen sacred word -- the sacred-word cue text is fixed regardless (no per-word cue
     * variant exists), so this has no engine effect yet; recorded in
     * [MeditationDefinition.variables] only, mirroring `dhikrPhrase` in
     * [com.pirxhio.affirmity.meditation.dhikr.DhikrConfig]. */
    val sacredWord: String? = null,
)

object CenteringPrayerText {
    const val SACRED_WORD = "meditation.centeringprayer.sacred_word"
    const val CLOSING = "meditation.centeringprayer.closing"
}

fun centeringPrayerMeditationDefinition(
    config: CenteringPrayerConfig = CenteringPrayerConfig(),
): MeditationDefinition {
    val children = buildList {
        if (config.includeSacredWord) {
            add(
                cuedPhase(
                    id = "sacred_word",
                    duration = PhaseDuration.Fixed(config.sacredWordMillis),
                    cueTextId = CenteringPrayerText.SACRED_WORD,
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
            cuedPhase(
                id = "closing",
                duration = PhaseDuration.Fixed(config.closingMillis),
                cueTextId = CenteringPrayerText.CLOSING,
            ),
        )
    }

    return MeditationDefinition(
        id = "centeringprayer",
        variables = mapOf("sacredWord" to config.sacredWord),
        root = MeditationSequence(id = "centeringprayer", children = children),
    )
}
