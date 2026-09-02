package com.pirxhio.affirmity.compass

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two seams [CompassAnswerRepository] extracts specifically so they're unit-testable
 * without mocking Android's [java.net.HttpURLConnection] -- the raw HTTP call itself follows
 * `BillingService.syncEntitlement()`'s established convention of NOT being unit-tested at this
 * layer (no androidTest harness in this repo mocks HttpURLConnection).
 */
class CompassAnswerRepositoryTest {

    @Test
    fun `request body carries the exact answered question id`() {
        assertEquals(
            """{"questionId":"gratitude_today"}""",
            buildAnswerCompassRequestBody("gratitude_today"),
        )
    }

    @Test
    fun `request body escapes a questionId containing quotes, backslashes, and newlines`() {
        // questionId is read off EXTRA_NOTIFICATION_QUESTION_ID on the launching Intent, and
        // MainActivity is android:exported="true" -- any other app on the device can supply an
        // arbitrary string here, so the body builder must produce valid, correctly-escaped JSON
        // no matter what that string contains, not just the app's own static catalog ids.
        val maliciousQuestionId = "gratitude\"today\\tomorrow\nnext_line"

        val body = buildAnswerCompassRequestBody(maliciousQuestionId)

        val roundTripped = JSONObject(body).getString("questionId")
        assertEquals(maliciousQuestionId, roundTripped)
    }

    @Test
    fun `a successful response cancels the reflection notification`() {
        var cancelled = false
        handleAnswerCompassResult(200) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test
    fun `a redirect-range response still counts as success`() {
        var cancelled = false
        handleAnswerCompassResult(299) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test
    fun `a client error response does not cancel the notification`() {
        var cancelled = false
        handleAnswerCompassResult(400) { cancelled = true }
        assertFalse(cancelled)
    }

    @Test
    fun `a server error response does not cancel the notification`() {
        var cancelled = false
        handleAnswerCompassResult(500) { cancelled = true }
        assertFalse(cancelled)
    }
}
