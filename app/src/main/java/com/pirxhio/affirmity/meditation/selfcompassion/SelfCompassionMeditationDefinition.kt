package com.pirxhio.affirmity.meditation.selfcompassion

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.authoring.RestKind
import com.pirxhio.affirmity.meditation.authoring.breathingBlock
import com.pirxhio.affirmity.meditation.authoring.restPhase

/**
 * "Autocompasión 5min" / "Autocompasión 10min" — catalog entries `autocompasion_5` (Free) /
 * `autocompasion_10` (Pro) (REQ-4.11.6/4.11.7, design §7.5). One parameterized builder, not two
 * files: the free/pro pair is a deliberate upsell shape and must visibly share structure. Shared
 * phase ids across the two [SelfCompassionLength] instances are safe — uniqueness is a
 * per-[MeditationDefinition] property, and the two are separate instances with distinct
 * [MeditationDefinition.id]s.
 *
 * SHORT: `15 + 100 + 70 + 70 + 45 = 300s` = 5 min exact.
 * LONG: `25 + 160 + 120 + 120 + 120 + 55 = 600s` = 10 min exact (adds `sc_phrase_3`).
 */
object SelfCompassionText {
    const val INTRO = "meditation.selfcompassion.intro"
    const val PHRASE_1 = "meditation.selfcompassion.phrase_1"
    const val PHRASE_2 = "meditation.selfcompassion.phrase_2"
    const val PHRASE_3 = "meditation.selfcompassion.phrase_3"
    const val CLOSING = "meditation.selfcompassion.closing"
}

enum class SelfCompassionLength(val definitionId: String) {
    SHORT("selfcompassion5"),
    LONG("selfcompassion10"),
}

fun selfCompassionMeditationDefinition(length: SelfCompassionLength): MeditationDefinition {
    val introMillis = if (length == SelfCompassionLength.SHORT) 15_000L else 25_000L
    val breaths = if (length == SelfCompassionLength.SHORT) 10 else 16
    val phraseMillis = if (length == SelfCompassionLength.SHORT) 70_000L else 120_000L
    val closeMillis = if (length == SelfCompassionLength.SHORT) 45_000L else 55_000L

    val children = buildList {
        add(
            Phase(
                id = "sc_intro",
                duration = PhaseDuration.Fixed(introMillis),
                onEnter = listOf(ShowText(SelfCompassionText.INTRO)),
            ),
        )
        add(
            breathingBlock(
                id = "sc_breathing",
                breaths = breaths,
                inhaleMillis = 4_000L,
                exhaleMillis = 6_000L,
                breathId = "sc_breath",
                inhaleId = "sc_inhale",
                exhaleId = "sc_exhale",
            ),
        )
        add(
            restPhase(
                id = "sc_phrase_1",
                kind = RestKind.OPEN_AWARENESS,
                duration = PhaseDuration.Fixed(phraseMillis),
                cueTextId = SelfCompassionText.PHRASE_1,
            ),
        )
        add(
            restPhase(
                id = "sc_phrase_2",
                kind = RestKind.OPEN_AWARENESS,
                duration = PhaseDuration.Fixed(phraseMillis),
                cueTextId = SelfCompassionText.PHRASE_2,
            ),
        )
        if (length == SelfCompassionLength.LONG) {
            add(
                restPhase(
                    id = "sc_phrase_3",
                    kind = RestKind.OPEN_AWARENESS,
                    duration = PhaseDuration.Fixed(120_000L),
                    cueTextId = SelfCompassionText.PHRASE_3,
                ),
            )
        }
        add(
            Phase(
                id = "sc_close",
                duration = PhaseDuration.Fixed(closeMillis),
                onEnter = listOf(ShowText(SelfCompassionText.CLOSING)),
            ),
        )
    }

    return MeditationDefinition(
        id = length.definitionId,
        root = MeditationSequence(id = length.definitionId, children = children),
    )
}
