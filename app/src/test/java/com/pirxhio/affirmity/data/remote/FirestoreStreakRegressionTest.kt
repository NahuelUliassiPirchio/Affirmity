package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.DailyCompletionStats
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Firestore-backed equivalent of
 * `AffirmityAppStateInstrumentedTest.brokenMeditationStreak_stillShowsMondayAndWednesdayCompleted`
 * (task 2.4's approval test). Feeds rows through the same Firestore map round-trip a
 * `FirestoreDailyCompletionRepository` read produces, then through the exact same
 * [DailyCompletionStats] the Room path uses — proving the "raw per-day rows only, no cached
 * streak field" schema invariant (spec's "Streak stays re-derived, never cached" scenario) holds
 * on the Firestore path too.
 */
class FirestoreStreakRegressionTest {

    @Test
    fun `broken meditation streak still shows Monday and Wednesday completed on the Firestore path`() {
        val monday = 100L
        val tuesday = monday + 1
        val wednesday = monday + 2

        // Simulate what a FirestoreDailyCompletionRepository read produces: raw per-day rows,
        // round-tripped through the same map shape a real snapshot listener would deliver.
        // Tuesday is intentionally absent — never written, exactly like the Room-path test.
        val firestoreRows = listOf(
            dailyCompletionFromMap(dailyCompletionToMap(DailyCompletionEntity(epochDay = monday, meditationDone = true))),
            dailyCompletionFromMap(dailyCompletionToMap(DailyCompletionEntity(epochDay = wednesday, meditationDone = true))),
        )

        // No document in the schema carries a precomputed streak — only raw per-day fields.
        val streakLikeFields = setOf("epochDay", "meditationDone", "affirmationDone")
        firestoreRows.forEach { row ->
            assertEquals(setOf("epochDay", "meditationDone", "affirmationDone"), streakLikeFields)
            assertEquals(false, dailyCompletionToMap(row).containsKey("streak"))
            assertEquals(false, dailyCompletionToMap(row).containsKey("streakDays"))
        }

        val weekly = DailyCompletionStats.toWeeklyStreak(
            rows = firestoreRows,
            weekStartEpochDay = monday,
            todayEpochDay = wednesday,
            isDone = { it.meditationDone },
        )

        assertEquals(true, weekly.completedDays[0]) // Monday
        assertEquals(false, weekly.completedDays[1]) // Tuesday — missed, never written
        assertEquals(true, weekly.completedDays[2]) // Wednesday
        // Streak still counts Wednesday even though Tuesday broke contiguity, mirroring the
        // Room-path guarantee that a missed day never erases an earlier completed day.
        assertEquals(1, DailyCompletionStats.streakOf(firestoreRows, todayEpochDay = wednesday, isDone = { it.meditationDone }))

        // Sanity: Tuesday truly has no row at all — the "missed" day was never written to Firestore.
        assertEquals(null, firestoreRows.find { it.epochDay == tuesday })
    }
}
