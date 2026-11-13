package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.MigrationConfirmationPreferences
import com.pirxhio.affirmity.data.local.QuietHoursSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake behind the narrow [FirestoreMigrationSource] seam — no real Firebase/Android dependency. */
private class FakeConfirmations : MigrationConfirmationStore {
    val confirmed = mutableSetOf<String>()
    override suspend fun isConfirmed(uid: String) = uid in confirmed
    override suspend fun confirm(uid: String) { confirmed += uid }
}

private class ThrowingConfirmations(private val read: Boolean, private val write: Boolean) : MigrationConfirmationStore {
    override suspend fun isConfirmed(uid: String): Boolean =
        if (read) throw java.io.IOException("corrupt datastore") else false
    override suspend fun confirm(uid: String) {
        if (write) throw java.io.IOException("disk full")
    }
}

private class FakeFirestoreMigrationSource(private var markerAlreadyExists: Boolean) : FirestoreMigrationSource {
    val committedChunks = mutableListOf<List<DocWrite>>()

    var markerChecks = 0
    var failOnChunk: Int? = null
    var markerServedFromCache = false

    override suspend fun readMarker(uid: String): MarkerRead = when {
        !markerExists(uid) -> MarkerRead.ABSENT
        markerServedFromCache -> MarkerRead.PRESENT_FROM_CACHE
        else -> MarkerRead.PRESENT_FROM_SERVER
    }

    override suspend fun markerExists(uid: String): Boolean {
        markerChecks++
        return markerAlreadyExists
    }

    override suspend fun commitChunk(writes: List<DocWrite>) {
        if (failOnChunk == committedChunks.size) throw java.io.IOException("offline mid-commit")
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
    quietHours = QuietHoursSettings(enabled = false, startMinute = 1380, endMinute = 420),
    migratedAt = 1_700_000_000_000L,
)

private fun multiChunkSnapshot(uid: String) =
    emptySnapshot(uid).copy(completions = (0L until 1000L).map { DailyCompletionEntity(epochDay = it) })

class FirestoreMigratorTest {

    private suspend fun FirestoreMigrator.ensure(uid: String, snapshotReads: IntArray = IntArray(1)) =
        ensureMigrated(uid) { snapshotReads[0]++; emptySnapshot(uid) }

    @Test
    fun `ensureMigrated is a no-op when the marker already exists`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = true)
        val migrator = FirestoreMigrator(source)

        val outcome = migrator.ensure("uid-1")

        assertTrue(source.committedChunks.isEmpty())
        assertEquals(MigrationOutcome.ALREADY_MIGRATED, outcome)
    }

    @Test
    fun `ensureMigrated commits every chunk from the migration plan when the marker is absent`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = false)
        val migrator = FirestoreMigrator(source)

        val outcome = migrator.ensure("uid-2")

        assertFalse(source.committedChunks.isEmpty())
        assertEquals(FirestorePaths.migratedMarkerDoc("uid-2"), source.committedChunks.last().last().path)
        assertEquals(MigrationOutcome.MIGRATED, outcome)
    }

    @Test
    fun `an observed marker is confirmed locally and the next launch makes zero Firestore calls`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = true)
        val confirmations = FakeConfirmations()
        val migrator = FirestoreMigrator(source, confirmations)
        val reads = IntArray(1)

        migrator.ensure("uid-1", reads)
        assertTrue(confirmations.isConfirmed("uid-1"))
        val checksAfterFirstLaunch = source.markerChecks

        val outcome = migrator.ensure("uid-1", reads)

        assertEquals(MigrationOutcome.SKIPPED_CONFIRMED_LOCALLY, outcome)
        assertEquals(checksAfterFirstLaunch, source.markerChecks)
        assertEquals("no local legacy data was read either", 0, reads[0])
        assertTrue(source.committedChunks.isEmpty())
    }

    @Test
    fun `a completed migration is confirmed locally once the marker write was awaited`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = false)
        val confirmations = FakeConfirmations()

        FirestoreMigrator(source, confirmations).ensure("uid-2")

        assertTrue(confirmations.isConfirmed("uid-2"))
    }

    @Test
    fun `a failed migration mid-commit is not confirmed and the next launch retries`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = false).apply { failOnChunk = 0 }
        val confirmations = FakeConfirmations()
        val migrator = FirestoreMigrator(source, confirmations)

        val failure = runCatching { migrator.ensure("uid-3") }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
        assertFalse(confirmations.isConfirmed("uid-3"))

        source.failOnChunk = null
        val checksBeforeRetry = source.markerChecks
        val outcome = migrator.ensure("uid-3")

        assertEquals(MigrationOutcome.MIGRATED, outcome)
        assertTrue(source.markerChecks > checksBeforeRetry)
        assertTrue(confirmations.isConfirmed("uid-3"))
    }

    @Test
    fun `a failed marker read is never confirmed`() = runBlocking {
        val failing = object : FirestoreMigrationSource {
            override suspend fun markerExists(uid: String): Boolean = throw java.io.IOException("unavailable")
            override suspend fun commitChunk(writes: List<DocWrite>) = Unit
        }
        val confirmations = FakeConfirmations()

        runCatching { FirestoreMigrator(failing, confirmations).ensure("uid-4") }

        assertFalse(confirmations.isConfirmed("uid-4"))
    }

    @Test
    fun `a confirmation for uid A never skips the migration for uid B`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = false)
        val confirmations = FakeConfirmations().apply { confirmed += "uid-A" }
        val migrator = FirestoreMigrator(source, confirmations)

        val outcome = migrator.ensure("uid-B")

        assertEquals(MigrationOutcome.MIGRATED, outcome)
        assertFalse(source.committedChunks.isEmpty())
        assertTrue(source.markerChecks > 0)
    }

    @Test
    fun `a failure on a later chunk of a multi-chunk migration is not confirmed`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = false).apply { failOnChunk = 2 }
        val confirmations = FakeConfirmations()

        val failure = runCatching {
            FirestoreMigrator(source, confirmations).ensureMigrated("uid-5") { multiChunkSnapshot("uid-5") }
        }.exceptionOrNull()

        assertTrue(failure is java.io.IOException)
        assertEquals("two chunks committed before the failure", 2, source.committedChunks.size)
        assertFalse(confirmations.isConfirmed("uid-5"))
    }

    @Test
    fun `a marker read failure surfaces to the caller and confirms nothing`() = runBlocking {
        val failing = object : FirestoreMigrationSource {
            override suspend fun markerExists(uid: String): Boolean = throw java.io.IOException("unavailable")
            override suspend fun commitChunk(writes: List<DocWrite>) = Unit
        }
        val confirmations = FakeConfirmations()

        val failure = runCatching { FirestoreMigrator(failing, confirmations).ensure("uid-6") }.exceptionOrNull()

        assertTrue(failure is java.io.IOException)
        assertFalse(confirmations.isConfirmed("uid-6"))
    }

    @Test
    fun `a cache-served marker skips this launch but is not persisted and the next launch checks again`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = true).apply { markerServedFromCache = true }
        val confirmations = FakeConfirmations()
        val migrator = FirestoreMigrator(source, confirmations)

        val first = migrator.ensure("uid-7")

        assertEquals(MigrationOutcome.ALREADY_MIGRATED, first)
        assertTrue(source.committedChunks.isEmpty())
        assertFalse(confirmations.isConfirmed("uid-7"))

        val checksBefore = source.markerChecks
        val second = migrator.ensure("uid-7")

        assertEquals(MigrationOutcome.ALREADY_MIGRATED, second)
        assertTrue("the next launch must hit the marker check again", source.markerChecks > checksBefore)
    }

    @Test
    fun `a cache-served marker is persisted once the server confirms it on a later launch`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = true).apply { markerServedFromCache = true }
        val confirmations = FakeConfirmations()
        val migrator = FirestoreMigrator(source, confirmations)
        migrator.ensure("uid-8")

        source.markerServedFromCache = false
        migrator.ensure("uid-8")

        assertTrue(confirmations.isConfirmed("uid-8"))
    }

    @Test
    fun `a server-served marker is persisted`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = true)
        val confirmations = FakeConfirmations()

        FirestoreMigrator(source, confirmations).ensure("uid-9")

        assertTrue(confirmations.isConfirmed("uid-9"))
    }

    @Test
    fun `a throwing confirmation store on read falls through to the server check and migrates`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = false)
        val reports = mutableListOf<String>()
        val migrator = FirestoreMigrator(source, ThrowingConfirmations(read = true, write = false)) { m, _ -> reports += m }

        val outcome = migrator.ensure("uid-10")

        assertEquals(MigrationOutcome.MIGRATED, outcome)
        assertTrue(source.markerChecks > 0)
        assertFalse(source.committedChunks.isEmpty())
        assertEquals(1, reports.size)
    }

    @Test
    fun `a corrupt confirmation store does not block an already migrated user`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = true)

        val outcome = FirestoreMigrator(source, ThrowingConfirmations(read = true, write = true)).ensure("uid-11")

        assertEquals(MigrationOutcome.ALREADY_MIGRATED, outcome)
        assertTrue(source.markerChecks > 0)
    }

    @Test
    fun `a throwing confirmation store on write still counts the migration as done and reports it`() = runBlocking {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = false)
        val reports = mutableListOf<Throwable>()
        val migrator = FirestoreMigrator(source, ThrowingConfirmations(read = false, write = true)) { _, c -> reports += c }

        val outcome = migrator.ensure("uid-12")

        assertEquals(MigrationOutcome.MIGRATED, outcome)
        assertEquals(1, reports.size)
    }

    @Test
    fun `cancellation from the confirmation store is never swallowed`() {
        val source = FakeFirestoreMigrationSource(markerAlreadyExists = true)
        val store = object : MigrationConfirmationStore {
            override suspend fun isConfirmed(uid: String): Boolean = throw kotlinx.coroutines.CancellationException("cancelled")
            override suspend fun confirm(uid: String) = Unit
        }

        val failure = runCatching { runBlocking { FirestoreMigrator(source, store).ensure("uid-13") } }.exceptionOrNull()

        assertTrue(failure is kotlinx.coroutines.CancellationException)
    }

    @Test
    fun `marker classification trusts only a server-served marker without pending writes`() {
        assertEquals(MarkerRead.ABSENT, classifyMarker(exists = false, isFromCache = false, hasPendingWrites = false))
        assertEquals(MarkerRead.ABSENT, classifyMarker(exists = false, isFromCache = true, hasPendingWrites = true))
        assertEquals(MarkerRead.PRESENT_FROM_SERVER, classifyMarker(exists = true, isFromCache = false, hasPendingWrites = false))
        assertEquals(MarkerRead.PRESENT_FROM_CACHE, classifyMarker(exists = true, isFromCache = true, hasPendingWrites = false))
        assertEquals(MarkerRead.PRESENT_FROM_CACHE, classifyMarker(exists = true, isFromCache = false, hasPendingWrites = true))
        assertEquals(MarkerRead.PRESENT_FROM_CACHE, classifyMarker(exists = true, isFromCache = true, hasPendingWrites = true))
    }

    @Test
    fun `backup and extraction rules actively exclude the migration confirmation store`() {
        val res = java.io.File("src/main/res/xml")
        fun activeFileExcludes(file: String, section: String): Boolean {
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.File(res, file))
            val sections = doc.getElementsByTagName(section)
            return (0 until sections.length).any { i ->
                val excludes = (sections.item(i) as org.w3c.dom.Element).getElementsByTagName("exclude")
                (0 until excludes.length).any { j ->
                    val e = excludes.item(j) as org.w3c.dom.Element
                    e.getAttribute("domain") == "file" &&
                        e.getAttribute("path") == MigrationConfirmationPreferences.BACKUP_EXCLUDED_PATH
                }
            }
        }

        assertTrue("full-backup-content", activeFileExcludes("backup_rules.xml", "full-backup-content"))
        assertTrue("cloud-backup", activeFileExcludes("data_extraction_rules.xml", "cloud-backup"))
        assertTrue("device-transfer", activeFileExcludes("data_extraction_rules.xml", "device-transfer"))
    }
}
