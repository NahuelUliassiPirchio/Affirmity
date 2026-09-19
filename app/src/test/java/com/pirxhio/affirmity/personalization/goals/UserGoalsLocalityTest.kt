package com.pirxhio.affirmity.personalization.goals

import com.pirxhio.affirmity.data.AffirmityAppState
import com.pirxhio.affirmity.data.repository.DataSession
import org.junit.Assert.assertTrue
import org.junit.Test

class UserGoalsLocalityTest {

    @Test
    fun `goals are absent from the local-to-Firestore DataSession boundary`() {
        val sessionTypes = listOf(
            DataSession::class.java,
            DataSession.Local::class.java,
            DataSession.Migrating::class.java,
            DataSession.Remote::class.java,
        )

        val exposedTypes = sessionTypes.flatMap { type ->
            type.declaredMethods.map { it.returnType } +
                type.declaredFields.map { it.type } +
                type.declaredConstructors.flatMap { it.parameterTypes.asList() }
        }

        assertTrue(
            "UserGoalsStore must remain device-local and outside DataSession/Firestore sync",
            exposedTypes.none { UserGoalsStore::class.java.isAssignableFrom(it) },
        )
    }

    @Test
    fun `sign-out state has no goals store dependency available to clear`() {
        val wiredTypes = AffirmityAppState::class.java.declaredFields.map { it.type } +
            AffirmityAppState::class.java.declaredConstructors.flatMap { it.parameterTypes.asList() } +
            AffirmityAppState::class.java.declaredMethods.flatMap { method ->
                method.parameterTypes.asList() + method.returnType
            }

        assertTrue(
            "AffirmityAppState/signOut must not own or clear the device-local UserGoalsStore",
            wiredTypes.none { UserGoalsStore::class.java.isAssignableFrom(it) },
        )
    }
}
