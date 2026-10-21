package com.pirxhio.affirmity.meditation.wimhof.voice

import com.pirxhio.affirmity.meditation.CompositeCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.StopAudio
import com.pirxhio.affirmity.meditation.wimhof.WimHofConfig
import com.pirxhio.affirmity.meditation.wimhof.wimHofMeditationDefinition
import kotlin.random.Random

/** A [Random] whose range draws are decided by [pick] (default: always the lower bound). */
class ScriptedRandom(private val pick: (from: Int, until: Int) -> Int = { from, _ -> from }) : Random() {
    override fun nextBits(bitCount: Int): Int = 0
    override fun nextInt(from: Int, until: Int): Int = pick(from, until)
}

data class VoicePlay(val id: String, val breathIndex: Int?, val atMillis: Long)

/**
 * Real [MeditationEngine] + real [WhmVoiceDirector] over a recording sink. The sink doubles as the
 * audio executor: it logs every command (engine and director), tracks the single playing voice and
 * records any overlap. Like the screen wiring, events go to the director first (the "tap"), then
 * to the engine.
 */
class WhmSessionHarness(
    config: WimHofConfig = WimHofConfig(),
    voice: Boolean = true,
    random: Random = Random(1),
    private val completeVoiceImmediately: Boolean = false,
) {
    val commands = mutableListOf<MeditationCommand>()
    val voicePlays = mutableListOf<VoicePlay>()
    val overlaps = mutableListOf<String>()

    /** Elapsed millis inside the current phase, as last ticked. Reset before a phase change. */
    var nowMs: Long = 0
        private set

    var playing: String? = null
        private set
    private var playStartMs: Long = 0

    private val sink = object : MeditationCommandExecutor {
        override fun execute(command: MeditationCommand) {
            commands += command
            when (command) {
                is PlayVoice -> {
                    if (playing != null) overlaps += "${command.audioId} started while $playing was playing"
                    playing = command.audioId
                    playStartMs = nowMs
                    voicePlays += VoicePlay(command.audioId, breathingIndex, nowMs)
                    if (completeVoiceImmediately) send(MeditationEvent.AudioCompleted(command.audioId))
                }
                is StopAudio -> if (command.audioId == null || command.audioId == playing) playing = null
                else -> Unit
            }
        }
    }

    private lateinit var engine: MeditationEngine
    val director: WhmVoiceDirector? = if (voice) WhmVoiceDirector(sink, { engine.state.value }, random) else null

    init {
        engine = MeditationEngine(
            wimHofMeditationDefinition(config),
            CompositeCommandExecutor(listOfNotNull(sink, director)),
        )
    }

    val state get() = engine.state.value
    val generation: Int get() = state.timerGeneration
    val breathingIndex: Int? get() = state.iterationCounts["breathing"]
    val phaseId: String? get() = state.currentPhaseId
    val voiceIds: List<String> get() = voicePlays.map { it.id }

    fun send(event: MeditationEvent) {
        // The clip has ended by the time its completion is reported.
        if (event is MeditationEvent.AudioCompleted && event.audioId == playing) playing = null
        director?.onEvent(event)
        engine.send(event)
    }

    fun start() {
        nowMs = 0
        send(MeditationEvent.Start)
    }

    fun completePhase() {
        nowMs = 0
        send(MeditationEvent.TimerCompleted(generation))
    }

    /** The double tap / Skip path. */
    fun next() {
        nowMs = 0
        send(MeditationEvent.Skip)
    }

    fun pause() = send(MeditationEvent.Pause)
    fun resume() = send(MeditationEvent.Resume)
    fun cancel() = send(MeditationEvent.Cancel)
    fun audioCompleted(id: String) = send(MeditationEvent.AudioCompleted(id))

    fun tick(elapsedMillis: Long) {
        nowMs = elapsedMillis
        send(MeditationEvent.TimerTick(generation, elapsedMillis, null))
    }

    fun rawTick(generation: Int, elapsedMillis: Long) =
        send(MeditationEvent.TimerTick(generation, elapsedMillis, null))

    fun rawCompleted(generation: Int) = send(MeditationEvent.TimerCompleted(generation))

    /** Completes the clip that is playing (and any clip that starts as a result) right away. */
    fun finishVoices() {
        while (true) audioCompleted(playing ?: break)
    }

    /** One inhale + exhale per breath. Each completion is followed by [finishVoices] when asked. */
    fun completeBreaths(count: Int, completeVoices: Boolean = true) {
        repeat(count) {
            completePhase()
            if (completeVoices) finishVoices()
            completePhase()
            if (completeVoices) finishVoices()
        }
    }

    /** Ticks every [stepMs] up to [targetMs]; with [autoFinish] a clip ends after its duration. */
    fun tickTo(targetMs: Long, autoFinish: Boolean = true, stepMs: Long = 250) {
        while (nowMs < targetMs) {
            val e = minOf(nowMs + stepMs, targetMs)
            nowMs = e
            if (autoFinish) {
                playing?.let { id ->
                    if (e >= playStartMs + (WhmClips.durationMillis(id) ?: 0L)) audioCompleted(id)
                }
            }
            tick(e)
        }
    }
}
