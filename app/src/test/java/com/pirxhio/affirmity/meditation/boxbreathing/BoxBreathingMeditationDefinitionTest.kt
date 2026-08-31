package com.pirxhio.affirmity.meditation.boxbreathing

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.StartAmbient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxBreathingMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs 6 rounds of inhale-hold-exhale-hold, 24 phases total, no PlayAudio or StartAmbient`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(boxBreathingMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(24, phaseCount) // 6 rounds * 4 phases
        assertTrue(executor.commands.contains(ShowText(BoxBreathingText.HOLD)))
        assertTrue(executor.commands.none { it is PlayAudio })
        assertTrue(executor.commands.none { it is StartAmbient })
    }

    @Test
    fun `custom rounds are respected`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(
            boxBreathingMeditationDefinition(BoxBreathingConfig(rounds = 2)),
            executor,
        )
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(8, phaseCount) // 2 rounds * 4 phases
    }
}
