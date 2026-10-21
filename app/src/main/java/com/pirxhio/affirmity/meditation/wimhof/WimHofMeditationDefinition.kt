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
import com.pirxhio.affirmity.meditation.StopAudio
import com.pirxhio.affirmity.meditation.authoring.breathingBlock
import com.pirxhio.affirmity.meditation.authoring.cuedPhase
import com.pirxhio.affirmity.meditation.wimhof.voice.WhmVoicePolicy

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
    /** Explicit recovery length; when null it follows [voiceGuide] (the voice clips need more room). */
    private val recoveryHoldOverrideMillis: Long? = null,
    /**
     * Resolved voice-guide switch (see `resolveVoiceGuide`). Adds the skippable intro and a round
     * intro per round, drops the breathing entry cue (the bowl replaces it) and lengthens recovery.
     */
    val voiceGuide: Boolean = false,
) {
    val recoveryHoldMillis: Long
        get() = recoveryHoldOverrideMillis ?: if (voiceGuide) WimHofTiming.RECOVERY_VOICE_MILLIS else WimHofTiming.RECOVERY_MILLIS

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
    const val INTRO = "meditation.wimhof.intro"
    const val ROUND_INTRO = "meditation.wimhof.round_intro"
    const val CLOSING = "meditation.wimhof.closing"
}

/** Phase and repeat ids shared by the definition and the voice director. */
object WimHofPhaseIds {
    const val INTRO = "intro"
    const val ROUND_INTRO = "round_intro"
    const val CLOSING = "closing"
    const val INHALE = "inhale"
    const val EXHALE_HOLD = "exhale_hold"
    const val RECOVERY_HOLD = "recovery_hold"

    /** Repeat ids (their iteration counts live in the runtime state). */
    const val ROUNDS = "rounds"
    const val BREATHING = "breathing"
}

/** Definition variable names shared by the definition and the voice director. */
object WimHofVariables {
    const val ROUNDS = "rounds"
    const val BREATHS_PER_ROUND = "breathsPerRound"
}

/** Phase lengths that the voice guide's clip layout is measured against. */
object WimHofTiming {
    const val INTRO_MILLIS = 25_500L
    const val ROUND_INTRO_MILLIS = 8_800L
    const val RECOVERY_MILLIS = 15_000L
    const val RECOVERY_VOICE_MILLIS = 16_400L
}

fun wimHofMeditationDefinition(config: WimHofConfig = WimHofConfig()): MeditationDefinition {
    require(config.rounds > 0) { "rounds must be > 0, got ${config.rounds}" }
    require(config.breathsPerRound > 0) { "breathsPerRound must be > 0, got ${config.breathsPerRound}" }

    val round = MeditationSequence(
        id = "round",
        children = listOfNotNull(
            if (config.voiceGuide) {
                cuedPhase(
                    id = WimHofPhaseIds.ROUND_INTRO,
                    duration = PhaseDuration.Fixed(WimHofTiming.ROUND_INTRO_MILLIS),
                    cueTextId = WimHofText.ROUND_INTRO,
                ).withEntryCue(MeditationCue.BreathworkBowl)
            } else {
                null
            },
            breathingBlock(
                id = WimHofPhaseIds.BREATHING,
                breaths = config.breathsPerRound,
                inhaleMillis = config.inhaleMillis,
                exhaleMillis = config.exhaleMillis,
                entryCue = if (config.voiceGuide) null else MeditationCue.RoundStart,
                inhaleCommands = sfx(config.breathSound, config.breathSpeed.inhaleSfxId),
                exhaleCommands = sfx(config.breathSound, config.breathSpeed.exhaleSfxId),
                skippableAsBlock = true,
            ),
            cuedPhase(
                id = WimHofPhaseIds.EXHALE_HOLD,
                duration = PhaseDuration.Fixed(config.exhaleHoldMillis),
                cueTextId = WimHofText.EXHALE_HOLD,
                skippable = true,
            ).withEntryCue(MeditationCue.ImportantTransition)
                .withBreathSfxStopped(config)
                .withSfx(config.breathSound, WimHofSfx.EXHALE_HOLD)
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

    val rounds = Repeat(
        id = WimHofPhaseIds.ROUNDS,
        child = round,
        strategy = FixedCountRepetition(config.rounds),
    )

    return MeditationDefinition(
        id = "wimhof",
        variables = mapOf(
            WimHofVariables.ROUNDS to config.rounds,
            WimHofVariables.BREATHS_PER_ROUND to config.breathsPerRound,
        ),
        root = if (config.voiceGuide) {
            MeditationSequence(id = "wimhof", children = listOf(intro(), rounds, closing()))
        } else {
            rounds
        },
    )
}

private fun closing(): Phase = cuedPhase(
    id = WimHofPhaseIds.CLOSING,
    duration = PhaseDuration.Fixed(WhmVoicePolicy.closingMillis()),
    cueTextId = WimHofText.CLOSING,
    skippable = true,
)

private fun intro(): Phase = cuedPhase(
    id = WimHofPhaseIds.INTRO,
    duration = PhaseDuration.Fixed(WimHofTiming.INTRO_MILLIS),
    cueTextId = WimHofText.INTRO,
    skippable = true,
)

private fun sfx(enabled: Boolean, id: String): List<PlayAudio> = if (enabled) listOf(PlayAudio(id)) else emptyList()

/** Skipping the breathing block can leave an inhale/exhale clip in flight; cut it so it never
 * overlaps the total-exhale SFX. At a natural entry the clips have already ended (each is no
 * longer than its phase), so this is a no-op there. */
private fun Phase.withBreathSfxStopped(config: WimHofConfig): Phase =
    if (config.breathSound) {
        copy(
            onEnter = onEnter + listOf(
                StopAudio(config.breathSpeed.inhaleSfxId),
                StopAudio(config.breathSpeed.exhaleSfxId),
            ),
        )
    } else {
        this
    }

private fun Phase.withSfx(enabled: Boolean, id: String): Phase = copy(onEnter = onEnter + sfx(enabled, id))
