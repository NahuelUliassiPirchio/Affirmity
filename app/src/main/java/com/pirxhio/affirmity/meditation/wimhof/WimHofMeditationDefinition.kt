package com.pirxhio.affirmity.meditation.wimhof

import com.pirxhio.affirmity.meditation.FixedCountRepetition
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayAudio
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
    val breathSpeed: WimHofBreathSpeed = WimHofBreathSpeed.NORMAL,
    /** When false the definition authors no `whm_sfx_*` [PlayAudio] at all. */
    val breathSound: Boolean = true,
    val exhaleHoldMillis: Long = DEFAULT_EXHALE_HOLD_MILLIS,
    val recoveryHoldMillis: Long = 15_000L,
) {
    val inhaleMillis: Long get() = breathSpeed.inhaleMillis
    val exhaleMillis: Long get() = breathSpeed.exhaleMillis

    companion object {
        const val DEFAULT_ROUNDS = 3
        const val DEFAULT_BREATHS_PER_ROUND = 30

        /** Ceiling (4:10) so the 4:00 time mark of the voice guide is never cut off. */
        const val DEFAULT_EXHALE_HOLD_MILLIS = 250_000L

        /** Typical hold, used for length estimates and the completion threshold (not the timer). */
        const val TYPICAL_EXHALE_HOLD_MILLIS = 90_000L
    }
}

/**
 * Breathing pace. The SFX ids follow the speed: each speed has its own inhale and exhale clip
 * (`whm_sfx_inhale_<key>` / `whm_sfx_exhale_<key>`). [key] is the persisted customization value.
 */
enum class WimHofBreathSpeed(val key: String, val inhaleMillis: Long, val exhaleMillis: Long) {
    SLOW("slow", 2_000L, 2_000L),
    NORMAL("normal", 1_700L, 1_700L),
    FAST("fast", 1_200L, 1_000L),
    ;

    val inhaleSfxId: String get() = "whm_sfx_inhale_$key"
    val exhaleSfxId: String get() = "whm_sfx_exhale_$key"

    companion object {
        /** Missing or off-list values fall back to [NORMAL] (an Options value has no clamp). */
        fun fromKey(key: String?): WimHofBreathSpeed = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

/** SFX ids that are not speed-dependent. Raw resource name == id. */
object WimHofSfx {
    const val EXHALE_HOLD = "whm_sfx_exhale_hold"
    const val INHALE_RECOVERY = "whm_sfx_inhale_recovery"
    const val PREFIX = "whm_sfx_"
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
                inhaleCommands = sfx(config.breathSound, config.breathSpeed.inhaleSfxId),
                exhaleCommands = sfx(config.breathSound, config.breathSpeed.exhaleSfxId),
            ),
            cuedPhase(
                id = WimHofPhaseIds.EXHALE_HOLD,
                duration = PhaseDuration.Fixed(config.exhaleHoldMillis),
                cueTextId = WimHofText.EXHALE_HOLD,
                skippable = true,
            ).withEntryCue(MeditationCue.ImportantTransition).withSfx(config.breathSound, WimHofSfx.EXHALE_HOLD)
                .copy(estimatedMillis = minOf(WimHofConfig.TYPICAL_EXHALE_HOLD_MILLIS, config.exhaleHoldMillis)),
            cuedPhase(
                id = WimHofPhaseIds.RECOVERY_HOLD,
                duration = PhaseDuration.Fixed(config.recoveryHoldMillis),
                cueTextId = WimHofText.RECOVERY_HOLD,
            ).withEntryCue(MeditationCue.SectionTransition).withSfx(config.breathSound, WimHofSfx.INHALE_RECOVERY).let {
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

private fun sfx(enabled: Boolean, id: String): List<PlayAudio> = if (enabled) listOf(PlayAudio(id)) else emptyList()

private fun Phase.withSfx(enabled: Boolean, id: String): Phase = copy(onEnter = onEnter + sfx(enabled, id))
