package com.pirxhio.affirmity.meditation

import com.pirxhio.affirmity.meditation.audio.MeditationCue
import com.pirxhio.affirmity.meditation.breathing.BreathingConfig
import com.pirxhio.affirmity.meditation.breathing.breathingMeditationDefinition
import com.pirxhio.affirmity.meditation.wimhof.WimHofConfig
import com.pirxhio.affirmity.meditation.wimhof.wimHofMeditationDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RoundCuesTest {

    private class Recorder : MeditationCommandExecutor {
        val commands = mutableListOf<MeditationCommand>()
        override fun execute(command: MeditationCommand) {
            commands += command
        }
    }

    private fun runToCompletion(definition: MeditationDefinition): List<MeditationCommand> {
        val recorder = Recorder()
        val engine = MeditationEngine(definition, recorder)
        engine.send(MeditationEvent.Start)
        while (engine.state.value.status == SessionStatus.Running) {
            val gen = (recorder.commands.last { it is StartTimer } as StartTimer).generation
            engine.send(MeditationEvent.TimerCompleted(gen))
        }
        return recorder.commands
    }

    private fun cues(commands: List<MeditationCommand>) = commands.filterIsInstance<PlayCue>().map { it.cue }

    private val roundCues = listOf(
        MeditationCue.RoundStart,
        MeditationCue.ImportantTransition,
        MeditationCue.SectionTransition,
        MeditationCue.RoundEnd,
    )

    @Test
    fun `a cue limited to the first iteration fires once per Repeat run, not per iteration`() {
        val definition = MeditationDefinition(
            id = "iter",
            root = Repeat(
                id = "loop",
                child = Phase(
                    id = "p",
                    duration = PhaseDuration.Fixed(10L),
                    onEnter = listOf(PlayCue(MeditationCue.RoundStart, onlyAtFirstIterationOf = "loop")),
                ),
                strategy = FixedCountRepetition(3),
            ),
        )

        val played = cues(runToCompletion(definition))

        assertEquals(listOf(MeditationCue.SessionStart, MeditationCue.RoundStart, MeditationCue.SessionEnd), played)
    }

    @Test
    fun `wim hof rings the round cues once per round with no per-breath sound`() {
        val config = WimHofConfig(rounds = 2, breathsPerRound = 3)

        val played = cues(runToCompletion(wimHofMeditationDefinition(config)))

        assertEquals(
            listOf(MeditationCue.SessionStart) + roundCues + roundCues.dropLast(1) + listOf(MeditationCue.SessionEnd),
            played,
        )
    }

    @Test
    fun `breathing rings the round cues once per round and replaces the silent retention audio`() {
        val config = BreathingConfig(rounds = 2, breathsPerRound = 3)

        val commands = runToCompletion(breathingMeditationDefinition(config))

        assertEquals(
            listOf(MeditationCue.SessionStart) + roundCues + roundCues.dropLast(1) + listOf(MeditationCue.SessionEnd),
            cues(commands),
        )
        assertFalse(commands.any { it is PlayAudio })
        assertEquals(2, commands.count { it == StartLap("retention") })
    }
}
