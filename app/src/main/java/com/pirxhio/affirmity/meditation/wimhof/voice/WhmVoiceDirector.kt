package com.pirxhio.affirmity.meditation.wimhof.voice

import com.pirxhio.affirmity.meditation.EndSession
import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.MeditationRuntimeState
import com.pirxhio.affirmity.meditation.PauseTimer
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.ResumeTimer
import com.pirxhio.affirmity.meditation.SessionDirector
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.StartTimer
import com.pirxhio.affirmity.meditation.StopAudio
import com.pirxhio.affirmity.meditation.wimhof.WimHofPhaseIds
import com.pirxhio.affirmity.meditation.wimhof.WimHofVariables
import kotlin.random.Random

/**
 * Spanish voice guide of the Wim Hof session. No Android dependency: it watches the engine's
 * commands (a [StartTimer] is a phase entry) and the raw event stream (ticks, audio completions)
 * and emits [PlayVoice]/[StopAudio] into [sink].
 *
 * It mirrors the engine's event filtering (the tap sees events BEFORE the engine drops stale
 * ones): ticks from another timer generation, ticks while paused, or while the session is not
 * running are ignored. Voice clips never overlap -- one [WhmVoiceSlot]: required clips (countdown,
 * TIEMPO, REC) queue, optional ones (RESP, RET) are skipped when it is busy or when they would run
 * into the next required mark. A section change ([StopAudio]), a skip and the end of the session
 * free the slot and drop the queue themselves, because the executor reports no completion for a
 * stopped clip.
 *
 * Work goes through [pending] and a [draining] flag so an AudioCompleted raised synchronously from
 * inside [sink] (a missing player completes right away) is handled after the current step, never
 * recursively. Main-thread confined, like the engine's executors.
 */
class WhmVoiceDirector(
    private val sink: MeditationCommandExecutor,
    private val stateProvider: () -> MeditationRuntimeState,
    private val random: Random = Random.Default,
) : SessionDirector {

    private sealed interface Input {
        data class PhaseEntry(val generation: Int, val state: MeditationRuntimeState) : Input
        data class Tick(val generation: Int, val elapsedMillis: Long) : Input
        data class AudioDone(val audioId: String) : Input
        data class EngineStop(val audioId: String?) : Input
        data class Paused(val paused: Boolean) : Input
        data object Reset : Input
    }

    private sealed interface Schedule {
        data object None : Schedule

        class Hold(var nextTiempo: Int, var nextRetAtMillis: Long) : Schedule

        class Recovery(var cuentaPlayed: Boolean) : Schedule

        /** Clips chained with a gap after each one ends (the intro). */
        class Chain(val clips: List<String>) : Schedule {
            var next = 1
            var nextAtMillis: Long? = null
        }

        /** Required clips at fixed offsets inside a phase (the round intro). */
        class Timed(val marks: ArrayDeque<Pair<Long, String>>) : Schedule
    }

    private val pending = ArrayDeque<Input>()
    private var draining = false

    private val slot = WhmVoiceSlot()
    private var generation: Int = NO_GENERATION
    private var paused = false
    private var schedule: Schedule = Schedule.None

    private var lastVoiceBreath = 0
    private var nextRespBreath = 0
    private var lastResp: Int? = null
    private var lastRet: Int? = null
    private var lastEntre: Int? = null

    /** Elapsed millis of the last accepted tick in the current phase. */
    private var lastElapsed = 0L

    private val tiempoMarks: List<Pair<Long, String>> =
        WhmClips.tiempoBySeconds.toSortedMap().map { (seconds, id) -> seconds * 1_000L to id }

    override fun execute(command: MeditationCommand) {
        when (command) {
            is StartTimer -> submit(Input.PhaseEntry(command.generation, stateProvider()))
            is PauseTimer -> submit(Input.Paused(true))
            is ResumeTimer -> submit(Input.Paused(false))
            is EndSession -> submit(Input.Reset)
            is StopAudio -> submit(Input.EngineStop(command.audioId))
            else -> Unit
        }
    }

    override fun onEvent(event: MeditationEvent) {
        when (event) {
            is MeditationEvent.TimerTick -> submit(Input.Tick(event.generation, event.elapsedMillis))
            is MeditationEvent.AudioCompleted -> submit(Input.AudioDone(event.audioId))
            else -> Unit
        }
    }

    private fun submit(input: Input) {
        pending.addLast(input)
        if (draining) return
        draining = true
        try {
            while (pending.isNotEmpty()) process(pending.removeFirst())
        } finally {
            draining = false
        }
    }

    private fun process(input: Input) {
        when (input) {
            is Input.PhaseEntry -> enterPhase(input.generation, input.state)
            is Input.Tick -> onTick(input)
            is Input.AudioDone -> onAudioDone(input.audioId)
            is Input.EngineStop ->
                if (input.audioId == null || input.audioId == slot.current) slot.release()
            is Input.Paused -> paused = input.paused
            Input.Reset -> reset()
        }
    }

    private fun onAudioDone(audioId: String) {
        slot.complete(audioId)?.let(::emit)
        val chain = schedule as? Schedule.Chain ?: return
        if (chain.clips.getOrNull(chain.next - 1) == audioId && chain.next < chain.clips.size) {
            chain.nextAtMillis = lastElapsed + WhmVoicePolicy.CHAIN_GAP_MILLIS
        }
    }

    private fun reset() {
        slot.release()
        generation = NO_GENERATION
        paused = false
        schedule = Schedule.None
    }

    // --- Phase entry ---------------------------------------------------------------------------

    private fun enterPhase(newGeneration: Int, state: MeditationRuntimeState) {
        generation = newGeneration
        paused = false
        schedule = Schedule.None
        lastElapsed = 0L
        when (state.currentPhaseId) {
            WimHofPhaseIds.INTRO -> {
                stopVoice()
                schedule = Schedule.Chain(WhmVoicePolicy.INTRO_CLIPS)
                playRequired(WhmVoicePolicy.INTRO_CLIPS.first())
            }
            WimHofPhaseIds.CLOSING -> {
                stopVoice()
                schedule = Schedule.Chain(WhmVoicePolicy.CLOSING_CLIPS)
                playRequired(WhmVoicePolicy.CLOSING_CLIPS.first())
            }
            WimHofPhaseIds.ROUND_INTRO -> {
                stopVoice()
                schedule = Schedule.Timed(roundIntroMarks(state))
            }
            WimHofPhaseIds.INHALE -> onInhale(state)
            WimHofPhaseIds.EXHALE_HOLD -> {
                stopVoice()
                schedule = Schedule.Hold(nextTiempo = 0, nextRetAtMillis = WhmVoicePolicy.retGapMillis(random))
                playRequired(WhmVoicePolicy.RET_INICIO)
            }
            WimHofPhaseIds.RECOVERY_HOLD -> {
                stopVoice()
                schedule = Schedule.Recovery(cuentaPlayed = false)
                playRequired(WhmVoicePolicy.REC_INICIO)
            }
        }
    }

    /** ENTRE (rounds after the first) right after the bowl, then RONDA_N / RONDA_FINAL aligned to the end. */
    private fun roundIntroMarks(state: MeditationRuntimeState): ArrayDeque<Pair<Long, String>> {
        val marks = ArrayDeque<Pair<Long, String>>()
        val round = state.iterationCounts[WimHofPhaseIds.ROUNDS] ?: return marks
        val rounds = state.variables[WimHofVariables.ROUNDS] as? Int ?: return marks
        if (round > 0) {
            val clip = pickClip(WhmVoicePolicy.ENTRE_CLIP_COUNT, lastEntre)
            lastEntre = clip
            marks += WhmVoicePolicy.ENTRE_AT_MILLIS to WhmVoicePolicy.entreClipId(clip)
        }
        val ronda = WhmVoicePolicy.rondaClipId(round, rounds)
        marks += WhmVoicePolicy.rondaStartMillis(ronda) to ronda
        return marks
    }

    private fun onInhale(state: MeditationRuntimeState) {
        val breath = state.iterationCounts[WimHofPhaseIds.BREATHING] ?: return
        val breaths = state.variables[WimHofVariables.BREATHS_PER_ROUND] as? Int ?: return
        if (breath == 0) {
            lastVoiceBreath = 0
            nextRespBreath = WhmVoicePolicy.respGapCycles(random)
        }
        // The gap is drawn on the due breath whatever happens to it, so a countdown clip taking that
        // breath cannot leave the schedule stuck on a breath that has already passed.
        val respDue = breath == nextRespBreath
        if (respDue) nextRespBreath = breath + WhmVoicePolicy.respGapCycles(random)
        val countdown = WhmVoicePolicy.countdownClip(breath, breaths)
        if (countdown != null) {
            playRequired(countdown)
            lastVoiceBreath = breath
            return
        }
        if (!respDue) return
        val clip = pickClip(WhmVoicePolicy.RESP_CLIP_COUNT, lastResp)
        val eligible = WhmVoicePolicy.respAllowed(breath, breaths) &&
            !slot.isBusy &&
            breath - lastVoiceBreath >= WhmVoicePolicy.RESP_MIN_GAP_CYCLES
        if (!eligible) return
        lastResp = clip
        lastVoiceBreath = breath
        play(WhmVoicePolicy.respClipId(clip))
    }

    // --- Ticks -----------------------------------------------------------------------------------

    private fun onTick(tick: Input.Tick) {
        if (tick.generation != generation || paused) return
        if (stateProvider().status != SessionStatus.Running) return
        lastElapsed = tick.elapsedMillis
        when (val current = schedule) {
            is Schedule.Chain -> onChainTick(current, tick.elapsedMillis)
            is Schedule.Timed -> onTimedTick(current, tick.elapsedMillis)
            is Schedule.Hold -> onHoldTick(current, tick.elapsedMillis)
            is Schedule.Recovery -> onRecoveryTick(current, tick.elapsedMillis)
            Schedule.None -> Unit
        }
    }

    private fun onChainTick(chain: Schedule.Chain, elapsed: Long) {
        val at = chain.nextAtMillis ?: return
        if (elapsed < at) return
        chain.nextAtMillis = null
        playRequired(chain.clips[chain.next++])
    }

    private fun onTimedTick(timed: Schedule.Timed, elapsed: Long) {
        while (timed.marks.isNotEmpty() && elapsed >= timed.marks.first().first) {
            playRequired(timed.marks.removeFirst().second)
        }
    }

    private fun onHoldTick(hold: Schedule.Hold, elapsed: Long) {
        while (hold.nextTiempo < tiempoMarks.size && elapsed >= tiempoMarks[hold.nextTiempo].first) {
            playRequired(tiempoMarks[hold.nextTiempo].second)
            hold.nextTiempo++
        }
        if (elapsed < hold.nextRetAtMillis) return
        val clip = pickClip(WhmVoicePolicy.RET_CLIP_COUNT, lastRet)
        val id = WhmVoicePolicy.retClipId(clip)
        val nextMark = tiempoMarks.getOrNull(hold.nextTiempo)?.first
        val clipMillis = WhmClips.durationMillis(id) ?: 0L
        if (!slot.isBusy && WhmVoicePolicy.fitsBefore(elapsed, clipMillis, nextMark)) {
            lastRet = clip
            play(id)
        }
        hold.nextRetAtMillis = elapsed + WhmVoicePolicy.retGapMillis(random)
    }

    private fun onRecoveryTick(recovery: Schedule.Recovery, elapsed: Long) {
        if (recovery.cuentaPlayed || elapsed < WhmVoicePolicy.REC_CUENTA_AT_MILLIS) return
        recovery.cuentaPlayed = true
        playRequired(WhmVoicePolicy.REC_CUENTA)
    }

    // --- Slot ---------------------------------------------------------------------------------------

    /** A random clip number in 1..[count] that differs from [last]. */
    private fun pickClip(count: Int, last: Int?): Int {
        val candidates = (1..count).filter { it != last }
        return candidates[random.nextInt(candidates.size)]
    }

    private fun playRequired(id: String) {
        if (slot.isBusy) slot.enqueue(id) else play(id)
    }

    private fun play(id: String) {
        slot.start(id)
        emit(id)
    }

    private fun emit(id: String) {
        sink.execute(PlayVoice(id, duckAmbientTo = null))
    }

    /** Cuts the playing clip and forgets the queue. */
    private fun stopVoice() {
        slot.release()?.let { sink.execute(StopAudio(it)) }
    }

    private companion object {
        const val NO_GENERATION = -1
    }
}
