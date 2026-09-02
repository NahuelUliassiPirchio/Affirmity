package com.pirxhio.affirmity.notifications

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

internal class FcmTokenOperationTimeoutException(operation: String) :
    Exception("FCM token $operation timed out")

/** Serializes token ownership changes across app-state sync and FirebaseMessagingService. */
class FcmTokenOwnershipCoordinator(
    private val mutex: Mutex = Mutex(),
    private val operationTimeoutMillis: Long = DEFAULT_OPERATION_TIMEOUT_MILLIS,
    private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    init {
        require(operationTimeoutMillis > 0)
    }

    suspend fun registerIfActive(
        uid: String,
        token: String,
        activeUid: () -> String?,
        register: suspend (uid: String, token: String, onFirebaseSuccess: () -> Unit) -> Unit,
        delete: suspend (uid: String, token: String) -> Unit,
    ) {
        val compensationClaimed = AtomicBoolean(false)
        fun compensateAfterLateSuccess() {
            if (activeUid() == uid || !compensationClaimed.compareAndSet(false, true)) return
            backgroundScope.launch {
                boundedCompensatingDelete(
                    uid = uid,
                    activeUid = activeUid,
                    delete = { delete(uid, token) },
                )
            }
        }

        // The timeout includes mutex acquisition as well as Firebase work so a queued ownership
        // transition cannot wait forever behind a stalled predecessor.
        val completed = withTimeoutOrNull(operationTimeoutMillis) {
            mutex.withLock {
                if (activeUid() != uid) return@withLock
                // Task.await() cancellation cannot cancel the underlying Firebase write. Attach
                // the callback before awaiting so late success remains observable.
                register(uid, token, ::compensateAfterLateSuccess)

                if (activeUid() != uid && compensationClaimed.compareAndSet(false, true)) {
                    try {
                        delete(uid, token)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        // Compensation is best-effort.
                    }
                }
            }
            true
        } ?: false
        if (!completed) throw FcmTokenOperationTimeoutException("registration")
    }

    suspend fun deleteBeforeSignOut(
        timeoutMillis: Long,
        delete: suspend () -> Unit,
        signOut: suspend () -> Unit,
        onDeleteFailure: (Throwable) -> Unit = {},
    ) {
        var signOutStarted = false
        val completedInOrder = withTimeoutOrNull(timeoutMillis) {
            mutex.withLock {
                try {
                    val deleteCompleted = withTimeoutOrNull(
                        minOf(timeoutMillis, operationTimeoutMillis),
                    ) {
                        delete()
                        true
                    } ?: false
                    if (!deleteCompleted) {
                        onDeleteFailure(FcmTokenOperationTimeoutException("deletion"))
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    onDeleteFailure(failure)
                }
                signOutStarted = true
                signOut()
            }
        } != null

        if (!completedInOrder && !signOutStarted) signOut()
    }

    private suspend fun boundedCompensatingDelete(
        uid: String,
        activeUid: () -> String?,
        delete: suspend () -> Unit,
    ) {
        try {
            withTimeoutOrNull(operationTimeoutMillis) {
                mutex.withLock {
                    // A same-UID re-login makes this token valid again. Recheck while holding the
                    // ownership mutex immediately before deletion so compensation cannot erase a
                    // newer registration for the active account.
                    if (activeUid() != uid) delete()
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Compensation is best-effort; ownership is rechecked before every registration too.
        }
    }

    private companion object {
        const val DEFAULT_OPERATION_TIMEOUT_MILLIS = 5_000L
    }
}

internal val processFcmTokenOwnershipCoordinator = FcmTokenOwnershipCoordinator()
