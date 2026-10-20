package com.pirxhio.affirmity.meditation.wimhof.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhmVoiceSlotTest {

    @Test
    fun `starting a clip makes the slot busy and completing it frees the slot`() {
        val slot = WhmVoiceSlot()
        assertFalse(slot.isBusy)

        slot.start("a")
        assertTrue(slot.isBusy)
        assertEquals("a", slot.current)

        assertNull(slot.complete("a"))
        assertFalse(slot.isBusy)
    }

    @Test
    fun `completing the current clip promotes the next queued clip in order`() {
        val slot = WhmVoiceSlot()
        slot.start("a")
        slot.enqueue("b")
        slot.enqueue("c")

        assertEquals("b", slot.complete("a"))
        assertEquals("b", slot.current)
        assertEquals("c", slot.complete("b"))
        assertNull(slot.complete("c"))
        assertFalse(slot.isBusy)
    }

    @Test
    fun `completing a clip that is not the current one changes nothing`() {
        val slot = WhmVoiceSlot()
        slot.start("a")
        slot.enqueue("b")

        assertNull(slot.complete("stale"))
        assertEquals("a", slot.current)
        assertEquals(listOf("b"), slot.queued)
    }

    @Test
    fun `release returns the interrupted clip and clears the queue`() {
        val slot = WhmVoiceSlot()
        slot.start("a")
        slot.enqueue("b")

        assertEquals("a", slot.release())

        assertFalse(slot.isBusy)
        assertTrue(slot.queued.isEmpty())
        assertNull(slot.release())
    }
}
