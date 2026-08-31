package com.pirxhio.affirmity.meditation.zazen

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.PlayAudio
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
        assertEquals(6, executor.commands.count { it is PlayAudio })
    }
}
