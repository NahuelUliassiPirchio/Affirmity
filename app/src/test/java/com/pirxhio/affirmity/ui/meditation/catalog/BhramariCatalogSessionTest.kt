package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.StartTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives the catalog's real Bhramari entry (default config) from Start to Completed. */
class BhramariCatalogSessionTest {

    private class Recorder : MeditationCommandExecutor {
        val commands = mutableListOf<MeditationCommand>()
        override fun execute(command: MeditationCommand) { commands += command }
    }

    @Test
    fun `catalog bhramari entry runs to completion and every text id resolves`() {
        val entry = findMeditationCatalogEntry("bhramari")
        assertNotNull(entry)
        val definition = entry!!.definition(emptyMap())
        val recorder = Recorder()
        val engine = MeditationEngine(definition, recorder)

        engine.send(MeditationEvent.Start)
        var guard = 0
        while (engine.state.value.status == SessionStatus.Running && guard++ < 100) {
            val gen = (recorder.commands.last { it is StartTimer } as StartTimer).generation
            engine.send(MeditationEvent.TimerCompleted(gen))
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        recorder.commands.filterIsInstance<ShowText>().forEach {
            assertTrue("unmapped text id ${it.textId}", it.textId in entry.presentation.textResources)
        }
    }
}
