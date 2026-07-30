package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.ChannelSettings
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.notifications.NotificationChannelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun snapshotWith(
    affirmations: List<AffirmationEntity> = emptyList(),
    completions: List<DailyCompletionEntity> = emptyList(),
): MigrationSnapshot = MigrationSnapshot(
    uid = "uid-1",
    affirmations = affirmations,
    completions = completions,
    meditationDurationSeconds = 300,
    notificationSettings = mapOf(
        NotificationChannelSpec.REMINDER to ChannelSettings(true, 540, 1260),
        NotificationChannelSpec.REFLECTION to ChannelSettings(false, 600, 900),
    ),
    migratedAt = 1_700_000_000_000L,
)

class MigrationPlanTest {

    @Test
    fun `plan covers every affirmation, completion and the preferences doc exactly once, marker last`() {
        val affirmations = (1..3).map { AffirmationEntity(id = "aff-$it", title = "t$it", subtitle = "s$it", backgroundType = "color", backgroundValue = "#000000") }
        val completions = (1..5).map { DailyCompletionEntity(epochDay = it.toLong(), meditationDone = true) }
        val snapshot = snapshotWith(affirmations, completions)

        val chunks = MigrationPlan.build(snapshot)
        val allWrites = chunks.flatten()

        // 3 affirmations + 5 completions + 1 preferences doc + 1 marker doc = 10
        assertEquals(10, allWrites.size)
        affirmations.forEach { aff ->
            assertTrue(allWrites.any { it.path == FirestorePaths.affirmationDoc(snapshot.uid, aff.id) })
        }
        completions.forEach { c ->
            assertTrue(allWrites.any { it.path == FirestorePaths.dailyCompletionDoc(snapshot.uid, c.epochDay) })
        }
        assertTrue(allWrites.any { it.path == FirestorePaths.settingsPreferencesDoc(snapshot.uid) })
        assertEquals(FirestorePaths.migratedMarkerDoc(snapshot.uid), chunks.last().last().path)
    }

    @Test
    fun `plan produces deterministic doc paths across two runs of the same snapshot`() {
        val affirmations = (1..3).map { AffirmationEntity(id = "aff-$it", title = "t$it", subtitle = "s$it", backgroundType = "color", backgroundValue = "#000000") }
        val completions = (1..5).map { DailyCompletionEntity(epochDay = it.toLong()) }
        val snapshot = snapshotWith(affirmations, completions)

        val firstRun = MigrationPlan.build(snapshot).flatten().map { it.path }
        val secondRun = MigrationPlan.build(snapshot).flatten().map { it.path }

        assertEquals(firstRun, secondRun)
    }

    @Test
    fun `the meta migrated marker write is always the last op in the plan's final chunk`() {
        val completions = (1..900).map { DailyCompletionEntity(epochDay = it.toLong()) }
        val snapshot = snapshotWith(completions = completions)

        val chunks = MigrationPlan.build(snapshot)

        val markerPath = FirestorePaths.migratedMarkerDoc(snapshot.uid)
        chunks.dropLast(1).forEach { chunk ->
            assertTrue(chunk.none { it.path == markerPath })
        }
        assertEquals(markerPath, chunks.last().last().path)
    }

    @Test
    fun `a snapshot large enough to exceed 450 ops is split into chunks of at most 450 ops each`() {
        val completions = (1..900).map { DailyCompletionEntity(epochDay = it.toLong()) }
        val snapshot = snapshotWith(completions = completions)

        val chunks = MigrationPlan.build(snapshot)

        assertTrue(chunks.size > 1)
        chunks.forEach { chunk -> assertTrue(chunk.size <= 450) }
        // 900 completions + 1 preferences doc + 1 marker doc = 902 ops total
        assertEquals(902, chunks.sumOf { it.size })
    }
}
