package com.pirxhio.affirmity.meditation.winddown

import com.pirxhio.affirmity.meditation.EndSession
import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.SessionEndReason
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.StartAmbient
import com.pirxhio.affirmity.meditation.StartTimer
import com.pirxhio.affirmity.meditation.StopAmbient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs [windDownMeditationDefinition] through [MeditationEngine] + a [RecordingCommandExecutor]
 * test double — no coroutines, no real clock, no Android, exactly like `MeditationEngineTest`
 * (design §9). Proves every row of design §8's coverage table for the ~90s "no gong, fade-out"
 * miniature of Spec 3's "Dormir 10min" requirement (spec §11, acceptance criterion 6).
 */
class WindDownMeditationDefinitionTest {

    private lateinit var executor: RecordingCommandExecutor
    private lateinit var engine: MeditationEngine

    @Before
    fun setUp() {
        executor = RecordingCommandExecutor()
        engine = MeditationEngine(windDownMeditationDefinition(), executor)
    }

    private val state get() = engine.state.value

    private fun lastTimerGeneration(): Int =
        (executor.commands.last { it is StartTimer } as StartTimer).generation

    @Test
    fun `settle starts the looping ambient bed with a fade-in and shows the settle cue`() {
        engine.send(MeditationEvent.Start)

        assertEquals(listOf("winddown", "settle"), state.activePath)
        assertTrue(
            executor.commands.contains(
                StartAmbient(WindDownAudio.BED, volume = 0.6f, fadeInMillis = 4_000L),
            ),
        )
        assertTrue(executor.commands.contains(ShowText(WindDownText.SETTLE)))
    }

    @Test
    fun `guidance shows its cue and plays a ducking voice line`() {
        engine.send(MeditationEvent.Start)
        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration()))

        assertEquals(listOf("winddown", "guidance"), state.activePath)
        assertTrue(executor.commands.contains(ShowText(WindDownText.GUIDANCE)))
        assertTrue(executor.commands.contains(PlayVoice(WindDownAudio.INTRO)))
    }

    @Test
    fun `AudioCompleted is a real phase exit trigger for guidance, advancing to rest`() {
        engine.send(MeditationEvent.Start)
        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration()))

        engine.send(MeditationEvent.AudioCompleted(WindDownAudio.INTRO))

        assertEquals(listOf("winddown", "rest"), state.activePath)
    }

    @Test
    fun `rest is an AMBIENT-kind rest phase showing only its cue, with no audio command of its own`() {
        engine.send(MeditationEvent.Start)
        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration()))
        engine.send(MeditationEvent.AudioCompleted(WindDownAudio.INTRO))

        assertEquals(listOf("winddown", "rest"), state.activePath)
        assertTrue(executor.commands.contains(ShowText(WindDownText.REST)))
        // The bed from `settle` is still playing — `rest` must not start a new voice/sound of its
        // own: the only PlayVoice in the whole trace so far is `guidance`'s.
        assertEquals(1, executor.commands.count { it is PlayVoice })
    }

    @Test
    fun `fadeout shows the closing cue and fades the ambient bed out inside the phase`() {
        engine.send(MeditationEvent.Start)
        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration()))
        engine.send(MeditationEvent.AudioCompleted(WindDownAudio.INTRO))
        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration()))

        assertEquals(listOf("winddown", "fadeout"), state.activePath)
        assertTrue(executor.commands.contains(ShowText(WindDownText.CLOSING)))
        assertTrue(executor.commands.contains(StopAmbient(fadeOutMillis = 5_000L)))
    }

    @Test
    fun `the session ends in Completed with the fade already dispatched and EndSession last, and no gong ever plays`() {
        engine.send(MeditationEvent.Start)
        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration()))
        engine.send(MeditationEvent.AudioCompleted(WindDownAudio.INTRO))
        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration()))
        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration()))

        assertEquals(SessionStatus.Completed, state.status)
        // `fadeout` authors StopAmbient on `onEnter` (design §5 EC-1's correct form), so it is
        // dispatched well before the terminal broadcast rather than immediately preceding it — the
        // onExit-authored variant of that ordering guarantee is separately pinned generically by
        // `MeditationEngineTest`'s `` `a fade dispatched by the final phase onExit is followed
        // immediately by EndSession` `` (design §8). Here we assert the WindDown-specific shape:
        // the fade command was dispatched, and the session's terminal command is EndSession(Completed).
        assertTrue(executor.commands.contains(StopAmbient(5_000L)))
        assertEquals(EndSession(SessionEndReason.Completed), executor.commands.last())
        // The whole-session "no gong, fade-out" requirement (spec §11 / acceptance criterion 6):
        // not one PlayAudio command is ever dispatched across the entire command trace.
        assertTrue(executor.commands.none { it is PlayAudio })
    }

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }
}
