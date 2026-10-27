package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.ActivityDailyFactDao
import com.pirxhio.affirmity.data.local.ActivityDao
import com.pirxhio.affirmity.data.local.ActivityOwnerEntity
import com.pirxhio.affirmity.data.repository.RoomActivitySession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActivitySessionBindingTest {
    @Test fun `suspended local writes bind initialized generation and never read global archive`() = runTest {
        val owner = CompletableDeferred<ActivityOwnerEntity>()
        val journal = Mockito.mock(ActivityDao::class.java)
        val daily = Mockito.mock(ActivityDailyFactDao::class.java)
        val binding = RoomActivitySession(owner, journal, daily)
        val write = async { binding.moods.upsert(20_000, 4, "note") }
        runCurrent()
        assertFalse(write.isCompleted)
        owner.complete(ActivityOwnerEntity("local:G", "device", null, true, true, "local:G"))
        write.await()
        Mockito.verify(daily).upsertMood("local:G", 20_000, 4, "note")
        assertEquals("local:G", binding.activity.identity.await().owner)
    }

    @Test fun `separate generation bindings do not redirect captured same-day completion`() = runTest {
        val journal = Mockito.mock(ActivityDao::class.java)
        val daily = Mockito.mock(ActivityDailyFactDao::class.java)
        val g = RoomActivitySession(CompletableDeferred(ActivityOwnerEntity("local:G", "device", null, true)), journal, daily)
        val h = RoomActivitySession(CompletableDeferred(ActivityOwnerEntity("local:H", "device", null, true)), journal, daily)
        g.completions.markMeditation(20_000)
        h.completions.markAffirmation(20_000)
        Mockito.verify(daily).markMeditation("local:G", 20_000)
        Mockito.verify(daily).markAffirmation("local:H", 20_000)
        assertEquals("local:H", h.activity.identity.await().owner)
    }

    @Test fun `room activity delegate forwards rearmFailed to the underlying repository`() = runTest {
        val journal = Mockito.mock(ActivityDao::class.java)
        val daily = Mockito.mock(ActivityDailyFactDao::class.java)
        val session = RoomActivitySession(CompletableDeferred(ActivityOwnerEntity("local:G", "device", null, true)), journal, daily)
        session.activity.repository.rearmFailed("local:G")
        // The Room repository rejects a foreign owner, so reaching it proves the call was forwarded.
        try {
            session.activity.repository.rearmFailed("local:other")
            org.junit.Assert.fail("delegate must forward to the owner-checked repository")
        } catch (_: IllegalArgumentException) {
        }
    }
}
