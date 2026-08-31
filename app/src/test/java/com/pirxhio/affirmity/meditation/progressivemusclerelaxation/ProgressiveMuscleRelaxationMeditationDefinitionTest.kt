package com.pirxhio.affirmity.meditation.progressivemusclerelaxation

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressiveMuscleRelaxationMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs settling, tense+relax per muscle group, then whole-body rest - 16 phases`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(progressiveMuscleRelaxationMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        // settling + 7 groups * (tense + relax) + whole-body rest
        assertEquals(16, phaseCount)
        assertEquals(
            7,
            executor.commands.count { it is ShowText && it.textId == ProgressiveMuscleRelaxationText.TENSE },
        )
    }

    @Test
    fun `custom muscle groups list changes the phase count`() {
        val executor = RecordingCommandExecutor()
        val config = ProgressiveMuscleRelaxationConfig(muscleGroups = listOf("feet", "legs"))
        val engine = MeditationEngine(progressiveMuscleRelaxationMeditationDefinition(config), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(6, phaseCount) // settling + 2 groups * 2 + whole-body rest
    }
}
