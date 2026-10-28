package com.pirxhio.affirmity.personalization.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserGoalTest {

    @Test
    fun `goal vocabulary uses stable unique ids and string-resource labels`() {
        val goals = UserGoal.all

        assertEquals(
            listOf("calm", "confidence", "self_love", "motivation", "connection", "change", "direction"),
            goals.map { it.id },
        )
        assertEquals(goals.size, goals.map { it.id }.toSet().size)
        assertTrue(goals.all { it.labelRes != 0 })
        assertEquals(goals.size, goals.map { it.labelRes }.toSet().size)
    }
}
