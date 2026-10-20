package com.pirxhio.affirmity.ui.meditation

import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoubleTapAdvanceTest {

    @Test
    fun `a running session with the flag on advances to the next phase`() {
        assertEquals(MeditationEvent.Next, doubleTapAdvanceEvent(SessionStatus.Running, doubleTapAdvances = true))
    }

    @Test
    fun `the flag off never advances`() {
        SessionStatus.entries.forEach {
            assertNull("status=$it", doubleTapAdvanceEvent(it, doubleTapAdvances = false))
        }
    }

    @Test
    fun `only Running advances even with the flag on`() {
        SessionStatus.entries.filter { it != SessionStatus.Running }.forEach {
            assertNull("status=$it", doubleTapAdvanceEvent(it, doubleTapAdvances = true))
        }
    }
}
