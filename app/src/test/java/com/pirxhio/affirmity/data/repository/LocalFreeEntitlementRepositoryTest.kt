package com.pirxhio.affirmity.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec: "Signed-out session is always Free" -- resolved without any Firestore read. */
class LocalFreeEntitlementRepositoryTest {

    @Test
    fun `always emits the Free entitlement`() = runBlocking {
        val repository = LocalFreeEntitlementRepository()

        assertEquals(Entitlement.Free, repository.observe().first())
    }
}
