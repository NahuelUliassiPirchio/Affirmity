package com.pirxhio.affirmity.personalization.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserGoalTest {

    @Test
    fun `goal vocabulary uses stable unique ids and user-facing labels`() {
        val goals = UserGoal.all

        assertEquals(
            listOf("calm", "confidence", "self_love", "motivation", "connection", "change", "direction"),
            goals.map { it.id },
        )
        assertEquals(goals.size, goals.map { it.id }.toSet().size)
        assertTrue(goals.all { it.label.isNotBlank() })
    }
}
