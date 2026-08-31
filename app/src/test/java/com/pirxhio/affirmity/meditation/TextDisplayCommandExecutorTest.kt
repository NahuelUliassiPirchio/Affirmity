package com.pirxhio.affirmity.meditation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextDisplayCommandExecutorTest {

    @Test
    fun `ShowText sets currentTextId and leaves currentLiteralText null`() {
        val executor = TextDisplayCommandExecutor()

        executor.execute(ShowText("cue.arrival"))

        assertEquals("cue.arrival", executor.currentTextId.value)
        assertNull(executor.currentLiteralText.value)
    }

    @Test
    fun `ShowLiteralText sets currentLiteralText and leaves currentTextId null`() {
        val executor = TextDisplayCommandExecutor()

        executor.execute(ShowLiteralText("I am capable."))

        assertEquals("I am capable.", executor.currentLiteralText.value)
        assertNull(executor.currentTextId.value)
    }

    @Test
    fun `ShowLiteralText after ShowText clears the previous text id`() {
        val executor = TextDisplayCommandExecutor()

        executor.execute(ShowText("cue.arrival"))
        executor.execute(ShowLiteralText("I am capable."))

        assertEquals("I am capable.", executor.currentLiteralText.value)
        assertNull(executor.currentTextId.value)
    }

    @Test
    fun `ShowText after ShowLiteralText clears the previous literal text`() {
        val executor = TextDisplayCommandExecutor()

        executor.execute(ShowLiteralText("I am capable."))
        executor.execute(ShowText("cue.arrival"))

        assertEquals("cue.arrival", executor.currentTextId.value)
        assertNull(executor.currentLiteralText.value)
    }

    @Test
    fun `other commands are ignored`() {
        val executor = TextDisplayCommandExecutor()

        executor.execute(PlayAudio("bed"))

        assertNull(executor.currentTextId.value)
        assertNull(executor.currentLiteralText.value)
    }
}
