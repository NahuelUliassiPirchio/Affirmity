package com.pirxhio.affirmity.meditation.coherentbreathing

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.ui.meditation.catalog.collectPhases
import org.junit.Assert.assertEquals
import org.junit.Test

class CoherentBreathingMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config (5 min at 6 breaths per minute) runs 30 breaths = 60 phases, totaling 300s`() {
        val definition = coherentBreathingMeditationDefinition()
        val phasesById = collectPhases(definition.root).associateBy(Phase::id)
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(definition, executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        var totalMillis = 0L
        while (engine.state.value.status == SessionStatus.Running) {
            val phase = phasesById.getValue(requireNotNull(engine.state.value.currentPhaseId))
            totalMillis += (phase.duration as PhaseDuration.Fixed).millis
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(60, phaseCount)
        assertEquals(300_000L, totalMillis)
    }
}
