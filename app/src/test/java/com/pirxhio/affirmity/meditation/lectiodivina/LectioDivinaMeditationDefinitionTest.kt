package com.pirxhio.affirmity.meditation.lectiodivina

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Test

class LectioDivinaMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs lectio, meditatio, oratio, contemplatio - 4 phases, contemplatio has no cue`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(lectioDivinaMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(4, phaseCount)
        assertEquals(1, executor.commands.count { it is ShowText && it.textId == LectioDivinaText.LECTIO })
        assertEquals(1, executor.commands.count { it is ShowText && it.textId == LectioDivinaText.MEDITATIO })
        assertEquals(1, executor.commands.count { it is ShowText && it.textId == LectioDivinaText.ORATIO })
        assertEquals(3, executor.commands.count { it is ShowText })
    }
}
