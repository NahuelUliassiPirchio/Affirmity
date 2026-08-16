package com.pirxhio.affirmity.meditation.breathing

import com.pirxhio.affirmity.meditation.FixedCountRepetition
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.StartLap
import com.pirxhio.affirmity.meditation.EndLap
import com.pirxhio.affirmity.meditation.authoring.breathingBlock

/**
 * A configurable breathing meditation, used as this engine's first (and demo) meditation type —
 * NOT a clinical implementation of any specific method. Structure:
 *
 * ```
 * Repeat(rounds)
 *   MeditationSequence(round)
 *     Repeat(breathsPerRound)
 *       MeditationSequence(breath) -> Phase(inhale), Phase(exhale)
 *     Phase(retention)   // fixed duration OR an explicit user action, whichever comes first
 *     Phase(recovery)
 * ```
 *
 * A second, unrelated meditation type is added the same way this one was: a new function building
 * a new [MeditationDefinition] tree out of the same primitives — no engine changes.
 */
data class BreathingConfig(
    val rounds: Int = 3,
    val breathsPerRound: Int = 10,
    val inhaleMillis: Long = 1_500,
    val exhaleMillis: Long = 1_500,
    val retentionMillis: Long = 15_000,
    val recoveryMillis: Long = 10_000,
)

/** Text ids the domain emits via [ShowText] — the UI layer maps these to localized strings. */
object BreathingText {
    const val INHALE = "meditation.breathing.inhale"
    const val EXHALE = "meditation.breathing.exhale"
    const val RETENTION = "meditation.breathing.retention"
    const val RECOVERY = "meditation.breathing.recovery"
}

/** Audio ids the domain emits via [PlayAudio] — the UI layer maps these to actual sound resources. */
object BreathingAudio {
    const val RETENTION_START = "meditation.breathing.retention_start"
}

/** Id of the retention [Phase]'s lap, recorded via [StartLap]/[EndLap] — how long the user actually
 * held retention is derivable from this lap's start/end timestamps. */
const val BREATHING_RETENTION_LAP_ID = "retention"

fun breathingMeditationDefinition(config: BreathingConfig): MeditationDefinition {
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
                inhaleTextId = BreathingText.INHALE,
                exhaleTextId = BreathingText.EXHALE,
                breathId = "breath",
                inhaleId = "inhale",
                exhaleId = "exhale",
            ),
            Phase(
                id = "retention",
                duration = PhaseDuration.Fixed(config.retentionMillis),
                onEnter = listOf(
                    ShowText(BreathingText.RETENTION),
                    PlayAudio(BreathingAudio.RETENTION_START),
                    StartLap(BREATHING_RETENTION_LAP_ID),
                ),
                onExit = listOf(EndLap(BREATHING_RETENTION_LAP_ID)),
                exitEvents = setOf(MeditationEvent.UserAction::class),
            ),
            Phase(
                id = "recovery",
                duration = PhaseDuration.Fixed(config.recoveryMillis),
                onEnter = listOf(ShowText(BreathingText.RECOVERY)),
            ),
        ),
    )

    return MeditationDefinition(
        id = "breathing-demo",
        variables = mapOf(
            "rounds" to config.rounds,
            "breathsPerRound" to config.breathsPerRound,
        ),
        root = Repeat(
            id = "rounds",
            child = round,
            strategy = FixedCountRepetition(config.rounds),
        ),
    )
}
