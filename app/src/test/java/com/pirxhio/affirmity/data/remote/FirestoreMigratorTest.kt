package com.pirxhio.affirmity.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake behind the narrow [FirestoreMigrationSource] seam — no real Firebase/Android dependency. */
private class FakeFirestoreMigrationSource(private var markerAlreadyExists: Boolean) : FirestoreMigrationSource {
    val committedChunks = mutableListOf<List<DocWrite>>()

    override suspend fun markerExists(uid: String): Boolean = markerAlreadyExists

    override suspend fun commitChunk(writes: List<DocWrite>) {
        committedChunks += writes
        if (writes.any { it.path.endsWith("/meta/migrated") }) markerAlreadyExists = true
    }
}

private fun emptySnapshot(uid: String) = MigrationSnapshot(
    uid = uid,
    affirmations = emptyList(),
    completions = emptyList(),
    moods = emptyList(),
    meditationDurationSeconds = null,
    notificationSettings = emptyMap(),
    migratedAt = 1_700_000_000_000L,
)

class FirestoreMigratorTest {

    @Test
    fun `ensureMigrated is a no-op when the marker already exists`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = true)
        val migrator = FirestoreMigrator(source)

        migrator.ensureMigrated(emptySnapshot("uid-1"))

        assertTrue(source.committedChunks.isEmpty())
    }

    @Test
    fun `ensureMigrated commits every chunk from the migration plan when the marker is absent`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = false)
        val migrator = FirestoreMigrator(source)

        migrator.ensureMigrated(emptySnapshot("uid-2"))

        assertFalse(source.committedChunks.isEmpty())
        assertEquals(FirestorePaths.migratedMarkerDoc("uid-2"), source.committedChunks.last().last().path)
    }
}
