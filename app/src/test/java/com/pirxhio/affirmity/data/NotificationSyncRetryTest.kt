package com.pirxhio.affirmity.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class NotificationSyncRetryTest {

    @Test
    fun `notification sync times out every stalled attempt and stops after three`() = runTest {
        var attempts = 0

        try {
            retryNotificationSync(
                attempts = 3,
                attemptTimeoutMillis = 100,
                delayMillis = { },
            ) {
                attempts++
                awaitCancellation()
            }
            fail("Expected the final attempt timeout")
        } catch (_: NotificationSyncAttemptTimeoutException) {
            // Expected.
        }

        assertEquals(3, attempts)
    }

    @Test
    fun `notification sync retries transient failures and eventually succeeds`() = runTest {
        var attempts = 0

        retryNotificationSync(attempts = 3, delayMillis = { }) {
            attempts++
            if (attempts < 3) error("transient")
        }

        assertEquals(3, attempts)
    }

    @Test
    fun `notification sync stops after its bounded attempt count`() = runTest {
        var attempts = 0

        try {
            retryNotificationSync(attempts = 3, delayMillis = { }) {
                attempts++
                error("still failing")
            }
            fail("Expected the final failure")
        } catch (expected: IllegalStateException) {
            assertEquals("still failing", expected.message)
        }

        assertEquals(3, attempts)
    }

    @Test(expected = CancellationException::class)
    fun `notification sync never retries cancellation`() = runTest {
        retryNotificationSync(attempts = 3, delayMillis = { }) {
            throw CancellationException("cancel")
        }
    }
}
