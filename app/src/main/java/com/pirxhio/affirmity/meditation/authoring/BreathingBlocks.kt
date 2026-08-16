package com.pirxhio.affirmity.meditation.authoring

import com.pirxhio.affirmity.meditation.FixedCountRepetition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.breathing.BreathingText

/**
 * A repeated inhale/exhale block — the reusable core of the original breathing demo,
 * parameterised. Returns a [Repeat] so it drops directly into a `MeditationSequence`'s children.
 *
 * Ids are explicit and defaulted rather than derived, because `MeditationRuntimeState.
 * iterationCounts` and `MeditationRuntimeState.activePath` are keyed by them and the UI reads
 * `iterationCounts["breathing"]`. A definition containing two breathing blocks must pass distinct
 * [id]s; distinct phase ids are optional (activePath still disambiguates by parent) but
 * recommended.
 *
 * Hold phases ([holdAfterInhaleMillis]/[holdAfterExhaleMillis]) are omitted from the produced
 * sequence entirely when their millis are `0L`, so the default output is structurally identical
 * to a plain inhale/exhale block.
 */
fun breathingBlock(
    id: String = "breathing",
    breaths: Int,
    inhaleMillis: Long,
    exhaleMillis: Long,
    inhaleTextId: String = BreathingText.INHALE,
    exhaleTextId: String = BreathingText.EXHALE,
    holdAfterInhaleMillis: Long = 0L,
    holdAfterExhaleMillis: Long = 0L,
    holdTextId: String? = null,
    breathId: String = "breath",
    inhaleId: String = "inhale",
    exhaleId: String = "exhale",
    holdAfterInhaleId: String = "hold_in",
    holdAfterExhaleId: String = "hold_out",
): Repeat {
    val children = buildList {
        add(
            Phase(
                id = inhaleId,
                duration = PhaseDuration.Fixed(inhaleMillis),
                onEnter = listOf(ShowText(inhaleTextId)),
            ),
        )
        if (holdAfterInhaleMillis > 0L) {
            add(
                Phase(
                    id = holdAfterInhaleId,
                    duration = PhaseDuration.Fixed(holdAfterInhaleMillis),
                    onEnter = holdTextId?.let { listOf(ShowText(it)) } ?: emptyList(),
                ),
            )
        }
        add(
            Phase(
                id = exhaleId,
                duration = PhaseDuration.Fixed(exhaleMillis),
                onEnter = listOf(ShowText(exhaleTextId)),
            ),
        )
        if (holdAfterExhaleMillis > 0L) {
            add(
                Phase(
                    id = holdAfterExhaleId,
                    duration = PhaseDuration.Fixed(holdAfterExhaleMillis),
                    onEnter = holdTextId?.let { listOf(ShowText(it)) } ?: emptyList(),
                ),
            )
        }
    }

    val breath = MeditationSequence(id = breathId, children = children)

    return Repeat(
        id = id,
        child = breath,
        strategy = FixedCountRepetition(breaths),
    )
}
