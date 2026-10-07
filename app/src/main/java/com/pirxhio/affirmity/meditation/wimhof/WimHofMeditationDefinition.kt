package com.pirxhio.affirmity.meditation.wimhof

import com.pirxhio.affirmity.meditation.FixedCountRepetition
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayCue
import com.pirxhio.affirmity.meditation.audio.MeditationCue
import com.pirxhio.affirmity.meditation.authoring.withEntryCue
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.authoring.breathingBlock
import com.pirxhio.affirmity.meditation.authoring.cuedPhase

/**
 * Wim Hof style guided rounds: [breathsPerRound] deep, unforced breaths, then an exhale hold
 * (breath out and stay empty), then a recovery inhale hold (one full breath in, held), repeated
 * [rounds] times.
 *
 * The exhale hold is user-releasable (see [WimHofPhaseIds.EXHALE_HOLD]) with [exhaleHoldMillis]
 * as its ceiling: the practitioner decides when they need air, the timer is only the upper bound.
 */
data class WimHofConfig(
    val rounds: Int = DEFAULT_ROUNDS,
    val breathsPerRound: Int = DEFAULT_BREATHS_PER_ROUND,
    val inhaleMillis: Long = 2_000L,
    val exhaleMillis: Long = 1_500L,
    val exhaleHoldMillis: Long = 60_000L,
    val recoveryHoldMillis: Long = 15_000L,
) {
    companion object {
        const val DEFAULT_ROUNDS = 3
        const val DEFAULT_BREATHS_PER_ROUND = 30
    }
}

object WimHofText {
    const val EXHALE_HOLD = "meditation.wimhof.exhale_hold"
    const val RECOVERY_HOLD = "meditation.wimhof.recovery_hold"
}

object WimHofPhaseIds {
    const val EXHALE_HOLD = "exhale_hold"
    const val RECOVERY_HOLD = "recovery_hold"
}

fun wimHofMeditationDefinition(config: WimHofConfig = WimHofConfig()): MeditationDefinition {
    require(config.rounds > 0) { "rounds must be > 0, got ${config.rounds}" }
    require(config.breathsPerRound > 0) { "breathsPerRound must be > 0, got ${config.breathsPerRound}" }

    val round = MeditationSequence(
        id = "round",
        children = listOf(
            breathingBlock(
                id = "breathing",
                breaths = config.breathsPerRound,
                inhaleMillis = config.inhaleMillis,
                exhaleMillis = config.exhaleMillis,
                entryCue = MeditationCue.RoundStart,
            ),
            cuedPhase(
                id = WimHofPhaseIds.EXHALE_HOLD,
                duration = PhaseDuration.Fixed(config.exhaleHoldMillis),
                cueTextId = WimHofText.EXHALE_HOLD,
                skippable = true,
            ).withEntryCue(MeditationCue.ImportantTransition),
            cuedPhase(
                id = WimHofPhaseIds.RECOVERY_HOLD,
                duration = PhaseDuration.Fixed(config.recoveryHoldMillis),
                cueTextId = WimHofText.RECOVERY_HOLD,
            ).withEntryCue(MeditationCue.SectionTransition).let {
                it.copy(onExit = it.onExit + PlayCue(MeditationCue.RoundEnd))
            },
        ),
    )

    return MeditationDefinition(
        id = "wimhof",
        variables = mapOf("rounds" to config.rounds, "breathsPerRound" to config.breathsPerRound),
        root = Repeat(
            id = "rounds",
            child = round,
            strategy = FixedCountRepetition(config.rounds),
        ),
    )
}
