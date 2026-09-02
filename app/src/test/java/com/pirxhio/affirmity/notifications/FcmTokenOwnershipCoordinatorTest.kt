package com.pirxhio.affirmity.notifications

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FcmTokenOwnershipCoordinatorTest {

    @Test
    fun `queued registration times out while waiting for the ownership mutex`() = runTest {
        val heldMutex = Mutex(locked = true)
        val coordinator = FcmTokenOwnershipCoordinator(
            mutex = heldMutex,
            operationTimeoutMillis = 100,
            backgroundScope = backgroundScope,
        )
        var registerCalls = 0

        try {
            coordinator.registerIfActive(
                uid = "A",
                token = "token",
                activeUid = { "A" },
                register = { _, _, _ -> registerCalls++ },
                delete = { _, _ -> Unit },
            )
            fail("Expected mutex acquisition to be bounded")
        } catch (_: FcmTokenOperationTimeoutException) {
            // Expected.
        } finally {
            heldMutex.unlock()
        }

        assertEquals(0, registerCalls)
    }

    @Test
    fun `stalled registration times out and cannot wedge the next ownership operation`() = runTest {
        val coordinator = FcmTokenOwnershipCoordinator(
            operationTimeoutMillis = 100,
            backgroundScope = backgroundScope,
        )
        val events = mutableListOf<String>()
        try {
            coordinator.registerIfActive(
                uid = "A",
                token = "token",
                activeUid = { "A" },
                register = { _, _, _ -> awaitCancellation() },
                delete = { _, _ -> events += "unexpected-delete" },
            )
            fail("Expected the bounded registration to time out")
        } catch (_: FcmTokenOperationTimeoutException) {
            events += "register-timeout"
        }
        coordinator.deleteBeforeSignOut(
            timeoutMillis = 100,
            delete = { events += "delete" },
            signOut = { events += "sign-out" },
        )

        assertEquals(listOf("register-timeout", "delete", "sign-out"), events)
    }

    @Test
    fun `stalled cleanup times out and still signs out`() = runTest {
        val coordinator = FcmTokenOwnershipCoordinator(
            operationTimeoutMillis = 100,
            backgroundScope = backgroundScope,
        )
        val neverCompletes = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        coordinator.deleteBeforeSignOut(
            timeoutMillis = 100,
            delete = {
                events += "delete-started"
                neverCompletes.await()
            },
            signOut = { events += "sign-out" },
        )

        assertEquals(listOf("delete-started", "sign-out"), events)
    }

    @Test
    fun `late Firebase success after departure performs bounded compensating deletion`() = runTest {
        val coordinator = FcmTokenOwnershipCoordinator(
            operationTimeoutMillis = 100,
            backgroundScope = backgroundScope,
        )
        val events = mutableListOf<String>()
        var activeUid: String? = "A"
        var reportFirebaseSuccess: () -> Unit = {}

        try {
            coordinator.registerIfActive(
                uid = "A",
                token = "token",
                activeUid = { activeUid },
                register = { _, _, onFirebaseSuccess ->
                    reportFirebaseSuccess = onFirebaseSuccess
                    awaitCancellation()
                },
                delete = { _, _ -> events += "compensating-delete" },
            )
            fail("Expected the bounded registration to time out")
        } catch (_: FcmTokenOperationTimeoutException) {
            events += "register-timeout"
        }
        activeUid = null
        reportFirebaseSuccess()
        runCurrent()

        assertEquals(listOf("register-timeout", "compensating-delete"), events)
    }

    @Test
    fun `late compensation skips deletion when the same UID has signed back in`() = runTest {
        val mutex = Mutex()
        val coordinator = FcmTokenOwnershipCoordinator(
            mutex = mutex,
            operationTimeoutMillis = 100,
            backgroundScope = backgroundScope,
        )
        val events = mutableListOf<String>()
        var activeUid: String? = "A"
        var reportFirebaseSuccess: () -> Unit = {}

        try {
            coordinator.registerIfActive(
                uid = "A",
                token = "token",
                activeUid = { activeUid },
                register = { _, _, onFirebaseSuccess ->
                    reportFirebaseSuccess = onFirebaseSuccess
                    awaitCancellation()
                },
                delete = { _, _ -> events += "delete" },
            )
            fail("Expected the bounded registration to time out")
        } catch (_: FcmTokenOperationTimeoutException) {
            // Expected.
        }

        mutex.lock()
        activeUid = null
        reportFirebaseSuccess()
        runCurrent()
        activeUid = "A"
        mutex.unlock()
        runCurrent()

        assertEquals(emptyList<String>(), events)
    }
}
