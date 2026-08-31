package com.pirxhio.affirmity.meditation.nadishodhana

import com.pirxhio.affirmity.meditation.FixedCountRepetition
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.ShowText

/**
 * Alternate nostril breathing: inhale-left, exhale-right, inhale-right, exhale-left, repeated
 * [rounds] times. Not a [com.pirxhio.affirmity.meditation.authoring.breathingBlock] — that helper
 * only models a single inhale/hold/exhale/hold cycle, not four distinct alternating phases — so
 * this builds the [Repeat]/[MeditationSequence]/[Phase] tree directly, the same way
 * `breathingBlock` itself does internally. [retention] optionally inserts a hold phase after each
 * inhale (before the nostril switch) -- spec-optional, off by default.
 */
data class NadiShodhanaConfig(
    val rounds: Int = 6,
    val breathMillis: Long = 4_000L,
    val retention: Boolean = false,
    val retentionMillis: Long = 4_000L,
)

object NadiShodhanaText {
    const val INHALE_LEFT = "meditation.nadishodhana.inhale_left"
    const val EXHALE_RIGHT = "meditation.nadishodhana.exhale_right"
    const val INHALE_RIGHT = "meditation.nadishodhana.inhale_right"
    const val EXHALE_LEFT = "meditation.nadishodhana.exhale_left"
    const val HOLD = "meditation.nadishodhana.hold"
}

fun nadiShodhanaMeditationDefinition(
    config: NadiShodhanaConfig = NadiShodhanaConfig(),
): MeditationDefinition {
    require(config.rounds > 0) { "rounds must be > 0, got ${config.rounds}" }

    fun breathPhase(id: String, textId: String) = Phase(
        id = id,
        duration = PhaseDuration.Fixed(config.breathMillis),
        onEnter = listOf(ShowText(textId)),
    )

    fun holdPhase(id: String) = Phase(
        id = id,
        duration = PhaseDuration.Fixed(config.retentionMillis),
        onEnter = listOf(ShowText(NadiShodhanaText.HOLD)),
    )

    val children = buildList {
        add(breathPhase("inhale_left", NadiShodhanaText.INHALE_LEFT))
        if (config.retention) add(holdPhase("hold_left"))
        add(breathPhase("exhale_right", NadiShodhanaText.EXHALE_RIGHT))
        add(breathPhase("inhale_right", NadiShodhanaText.INHALE_RIGHT))
        if (config.retention) add(holdPhase("hold_right"))
        add(breathPhase("exhale_left", NadiShodhanaText.EXHALE_LEFT))
    }

    val cycle = MeditationSequence(id = "cycle", children = children)

    return MeditationDefinition(
        id = "nadishodhana",
        variables = mapOf("rounds" to config.rounds),
        root = Repeat(
            id = "breathing",
            child = cycle,
            strategy = FixedCountRepetition(config.rounds),
        ),
    )
}
