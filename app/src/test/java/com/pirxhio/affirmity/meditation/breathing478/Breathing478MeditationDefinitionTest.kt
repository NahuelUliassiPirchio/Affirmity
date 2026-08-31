package com.pirxhio.affirmity.meditation.breathing478

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

class Breathing478MeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config totals 4 rounds of 4s inhale, 7s hold, 8s exhale = 76s, 12 phases`() {
        val definition = breathing478MeditationDefinition()
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
        assertEquals(12, phaseCount) // 4 rounds * 3 phases (inhale, hold, exhale)
        assertEquals(76_000L, totalMillis)
    }
}
