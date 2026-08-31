package com.pirxhio.affirmity.meditation.hitbodedut

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HitbodedutMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs arrival, 3 reflection topics, personal prayer, then silence - 6 phases`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(hitbodedutMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(6, phaseCount)
    }

    @Test
    fun `guidedPrompts disabled collapses reflection to one unguided silent phase - 4 phases`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(
            hitbodedutMeditationDefinition(HitbodedutConfig(guidedPromptsEnabled = false)),
            executor,
        )
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(4, phaseCount)
    }

    @Test
    fun `all six prompt topics run in canonical order regardless of input order`() {
        val definition = hitbodedutMeditationDefinition(
            HitbodedutConfig(
                promptTopics = listOf("forgiveness", "gratitude", "purpose"),
            ),
        )
        val sequence = definition.root as com.pirxhio.affirmity.meditation.MeditationSequence
        val topicPhaseIds = sequence.children
            .map { it.id }
            .filter { it.startsWith("reflection_") }
        assertEquals(listOf("reflection_gratitude", "reflection_purpose", "reflection_forgiveness"), topicPhaseIds)
    }
}
