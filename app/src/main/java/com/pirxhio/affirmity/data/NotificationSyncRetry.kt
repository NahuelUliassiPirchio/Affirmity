package com.pirxhio.affirmity.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal class NotificationSyncAttemptTimeoutException :
    Exception("Notification token/timezone sync attempt timed out")

/** Small in-process retry for the signed-in timezone/token handshake. The surrounding session
 * collector runs once per distinct active UID transition; each complete attempt is bounded so a
 * stalled timezone write or token fetch advances to the next attempt instead of wedging forever. */
internal suspend fun retryNotificationSync(
    attempts: Int = 3,
    attemptTimeoutMillis: Long = 10_000L,
    delayMillis: suspend (failedAttemptIndex: Int) -> Unit = { failedAttemptIndex ->
        delay(250L * (1L shl failedAttemptIndex))
    },
    block: suspend () -> Unit,
) {
    require(attempts > 0)
    require(attemptTimeoutMillis > 0)
    repeat(attempts) { attemptIndex ->
        try {
            val completed = withTimeoutOrNull(attemptTimeoutMillis) {
                block()
                true
            } ?: false
            if (!completed) throw NotificationSyncAttemptTimeoutException()
            return
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            if (attemptIndex == attempts - 1) throw failure
            delayMillis(attemptIndex)
        }
    }
}
