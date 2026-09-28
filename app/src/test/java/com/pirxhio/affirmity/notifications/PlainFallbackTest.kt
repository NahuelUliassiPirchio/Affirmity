package com.pirxhio.affirmity.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlainFallbackTest {

    @Test
    fun `styled post success never touches the plain path`() {
        val calls = mutableListOf<String>()

        postWithPlainFallback(usesCustomViews = true, postStyled = { calls += "styled" }, postPlain = { calls += "plain" }) {
            calls += "fallback"
        }

        assertEquals(listOf("styled"), calls)
    }

    @Test
    fun `styled failure logs and posts the plain notification`() {
        val calls = mutableListOf<String>()
        var logged: Exception? = null

        postWithPlainFallback(
            usesCustomViews = true,
            postStyled = { throw IllegalStateException("bad remote views") },
            postPlain = { calls += "plain" },
        ) { logged = it }

        assertEquals(listOf("plain"), calls)
        assertTrue(logged is IllegalStateException)
    }

    @Test
    fun `a plain-only spec does not retry and propagates failures`() {
        try {
            postWithPlainFallback(
                usesCustomViews = false,
                postStyled = { throw IllegalStateException("boom") },
                postPlain = { fail("plain must not be retried") },
            ) { fail("no fallback expected") }
            fail("expected exception")
        } catch (expected: IllegalStateException) {
            assertEquals("boom", expected.message)
        }
    }

    @Test
    fun `if the plain fallback also fails the failure propagates (caller records no NOTIFY_POSTED)`() {
        try {
            postWithPlainFallback(
                usesCustomViews = true,
                postStyled = { throw IllegalStateException("styled") },
                postPlain = { throw IllegalStateException("plain") },
            ) {}
            fail("expected exception")
        } catch (expected: IllegalStateException) {
            assertEquals("plain", expected.message)
        }
    }

    @Test
    fun `only custom-view specs use custom views`() {
        assertTrue(NotificationStyleSpec.Streak("t", "b", 1, null).usesCustomViews())
        assertTrue(NotificationStyleSpec.Reflection("t", "b").usesCustomViews())
        assertTrue(NotificationStyleSpec.Mood("t", "b").usesCustomViews())
        assertEquals(false, NotificationStyleSpec.Plain("t", "b").usesCustomViews())
    }
}
