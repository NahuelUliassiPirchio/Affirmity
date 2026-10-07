package com.pirxhio.affirmity.meditation.zazen

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.MeditationNode
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.PlayCue
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.audio.MeditationCue
import com.pirxhio.affirmity.meditation.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ZazenMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config strikes 3 opening bells, posture, silence, then 3 closing bells - 8 phases`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(zazenMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(8, phaseCount) // 3 opening strikes + posture + silence + 3 closing strikes
        // Opening/closing are covered by the global SessionStart/SessionEnd cues, not per-strike audio.
        assertEquals(0, executor.commands.count { it is PlayAudio })
    }

    private fun phases(node: MeditationNode): List<Phase> = when (node) {
        is Phase -> listOf(node)
        is MeditationSequence -> node.children.flatMap(::phases)
        is Repeat -> phases(node.child)
    }

    private fun silenceSegments(config: ZazenConfig) =
        phases(zazenMeditationDefinition(config).root).filter { it.id.startsWith("silence") }

    private fun bellCount(segments: List<Phase>) =
        segments.sumOf { p -> p.onEnter.count { it is PlayCue && it.cue == MeditationCue.ZazenBell } }

    private fun millis(p: Phase) = (p.duration as PhaseDuration.Fixed).millis

    @Test
    fun `20 min silence with a 5 min interval rings 3 bells and keeps the total silence`() {
        val config = ZazenConfig(silenceMillis = 20 * 60_000L, intervalBellMinutes = 5)
        val segments = silenceSegments(config)

        assertEquals(4, segments.size)
        assertEquals(3, bellCount(segments))
        assertEquals(config.silenceMillis, segments.sumOf(::millis))
        assertEquals(0, segments.first().onEnter.size) // no bell at the very start
    }

    @Test
    fun `a non-multiple silence keeps its exact total and rings only inside the span`() {
        val config = ZazenConfig(silenceMillis = 12 * 60_000L, intervalBellMinutes = 5)
        val segments = silenceSegments(config)

        assertEquals(2, bellCount(segments))
        assertEquals(config.silenceMillis, segments.sumOf(::millis))
        assertEquals(2 * 60_000L, millis(segments.last()))
    }

    @Test
    fun `interval off rings no bells and keeps a single silence phase`() {
        val segments = silenceSegments(ZazenConfig(intervalBellMinutes = 0))

        assertEquals(1, segments.size)
        assertEquals(0, bellCount(segments))
    }

    @Test
    fun `interval at or beyond the silence span rings no bells`() {
        val config = ZazenConfig(silenceMillis = 10 * 60_000L, intervalBellMinutes = 10)
        val segments = silenceSegments(config)

        assertEquals(1, segments.size)
        assertEquals(0, bellCount(segments))
        assertEquals(0, bellCount(silenceSegments(config.copy(intervalBellMinutes = 15))))
    }

    @Test
    fun `interval bells do not change the total session duration`() {
        fun total(config: ZazenConfig) = phases(zazenMeditationDefinition(config).root).sumOf(::millis)

        assertEquals(total(ZazenConfig()), total(ZazenConfig(intervalBellMinutes = 2)))
    }
}
