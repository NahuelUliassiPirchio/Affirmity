package com.pirxhio.affirmity.notifications

import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectionPromptsTest {

    @Test
    fun `random returns a member of the bundled question bank`() {
        repeat(20) { seed ->
            val question = ReflectionPrompts.random(Random(seed))
            assertTrue(question in ReflectionPrompts.questions)
        }
    }

    @Test
    fun `question bank is non-empty`() {
        assertTrue(ReflectionPrompts.questions.isNotEmpty())
    }
}
