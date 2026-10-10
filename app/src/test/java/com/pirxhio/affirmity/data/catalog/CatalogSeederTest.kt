package com.pirxhio.affirmity.data.catalog

import com.pirxhio.affirmity.data.local.CatalogAffirmationDao
import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
import com.pirxhio.affirmity.data.local.CatalogPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val COLLECTION_ID = "self_worth.feeling_enough.intrinsic_worth"
private val KNOWN_COLLECTION_IDS = setOf(COLLECTION_ID)

private fun catalogJson(version: String, rowCount: Int = 1) = buildString {
    append("""{"version":"$version","affirmations":[""")
    (1..rowCount).forEach { i ->
        if (i > 1) append(",")
        append(
            """{"id":"cat_$COLLECTION_ID.00$i","title":"Texto $i","subtitle":"Subtexto $i","groupId":"self_worth",""" +
                """"themeId":"self_worth.feeling_enough","collectionId":"$COLLECTION_ID","sortOrder":${i - 1},""" +
                """"tone":"powerful","semanticAngle":"identity"}""",
        )
    }
    append("]}")
}

private class RecordingFakeDao(private val existingRows: Int? = null) : CatalogAffirmationDao {
    val calls = mutableListOf<String>()
    var lastReplaced: List<CatalogAffirmationEntity> = emptyList()

    override fun observeAll(): Flow<List<CatalogAffirmationEntity>> = throw NotImplementedError()
    override fun observeByGroupIds(groupIds: Set<String>): Flow<List<CatalogAffirmationEntity>> = throw NotImplementedError()
    override suspend fun getByIds(ids: List<String>): List<CatalogAffirmationEntity> = emptyList()
    override suspend fun count(): Int = existingRows ?: lastReplaced.size

    override suspend fun replaceAll(rows: List<CatalogAffirmationEntity>) {
        calls += "replaceAll"
        lastReplaced = rows
    }

    override suspend fun insertAll(rows: List<CatalogAffirmationEntity>) = throw NotImplementedError()
    override suspend fun deleteAll() = throw NotImplementedError()
}

private class RecordingFakePrefs(initial: String? = null, private val throwOnSave: Boolean = false) : CatalogPreferences {
    val calls = mutableListOf<String>()
    private val state = MutableStateFlow(initial)

    override fun observeSeededCatalogVersion(): Flow<String?> = state

    override suspend fun saveSeededCatalogVersion(version: String) {
        calls += "saveSeededCatalogVersion"
        if (throwOnSave) throw IllegalStateException("boom")
        state.value = version
    }
}

private fun testSeeder(
    reader: CatalogAssetReader,
    dao: CatalogAffirmationDao,
    prefs: CatalogPreferences,
    locale: () -> CatalogLocale = { CatalogLocale.ES },
) = CatalogSeeder(
    assetReader = reader,
    dao = dao,
    prefs = prefs,
    currentLocale = locale,
    knownCollectionIds = { KNOWN_COLLECTION_IDS },
)

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogSeederTest {

    @Test
    fun `seeds when the marker is absent`() = runBlocking {
        val dao = RecordingFakeDao()
        val prefs = RecordingFakePrefs(initial = null)
        val seeder = testSeeder({ catalogJson("1.0.0") }, dao, prefs)

        seeder.seedIfNeeded()

        assertEquals(listOf("replaceAll"), dao.calls)
        assertEquals(1, dao.lastReplaced.size)
        assertEquals("1.0.0|es", prefs.observeSeededCatalogVersion().value())
    }

    /** Covers spec catalog-tone-metadata + design D1: a version-bumped reseed backfills the new
     * nullable `tone`/`semanticAngle` columns for existing installs "for free" via the full
     * replace -- no separate backfill code path needed. */
    @Test
    fun `a version-bumped reseed backfills tone and semanticAngle via the full replace`() = runBlocking {
        val dao = RecordingFakeDao()
        val prefs = RecordingFakePrefs(initial = "0.9.0|es")
        val seeder = testSeeder({ catalogJson("1.0.0") }, dao, prefs)

        seeder.seedIfNeeded()

        assertEquals(listOf("replaceAll"), dao.calls)
        assertEquals("powerful", dao.lastReplaced.single().tone)
        assertEquals("identity", dao.lastReplaced.single().semanticAngle)
    }

    @Test
    fun `seeds when the marker is stale`() = runBlocking {
        val dao = RecordingFakeDao()
        val prefs = RecordingFakePrefs(initial = "0.9.0|es")
        val seeder = testSeeder({ catalogJson("1.0.0") }, dao, prefs)

        seeder.seedIfNeeded()

        assertEquals(listOf("replaceAll"), dao.calls)
        assertEquals("1.0.0|es", prefs.observeSeededCatalogVersion().value())
    }

    @Test
    fun `no-ops when the marker already matches the bundled version and rows exist`() = runBlocking {
        val dao = RecordingFakeDao(existingRows = 1)
        val prefs = RecordingFakePrefs(initial = "1.0.0|es")
        val seeder = testSeeder({ catalogJson("1.0.0") }, dao, prefs)

        seeder.seedIfNeeded()

        assertTrue(dao.calls.isEmpty())
    }

    /** Auto Backup restores DataStore (the marker) but excludes the Room DB, so a restored or
     * cleared-DB install has a matching marker over an empty table. The marker alone must not
     * gate seeding. */
    @Test
    fun `re-seeds when the marker matches but the catalog table is empty`() = runBlocking {
        val dao = RecordingFakeDao(existingRows = 0)
        val prefs = RecordingFakePrefs(initial = "1.0.0|es")
        val seeder = testSeeder({ catalogJson("1.0.0") }, dao, prefs)

        seeder.seedIfNeeded()

        assertEquals(listOf("replaceAll"), dao.calls)
        assertEquals(1, dao.lastReplaced.size)
    }

    @Test
    fun `a throwing saveSeededCatalogVersion still leaves rows committed and re-seeds cleanly next call`() = runBlocking {
        val dao = RecordingFakeDao()
        val prefs = RecordingFakePrefs(initial = null, throwOnSave = true)
        val seeder = testSeeder({ catalogJson("1.0.0") }, dao, prefs)

        assertThrows(IllegalStateException::class.java) { runBlocking { seeder.seedIfNeeded() } }
        assertEquals(listOf("replaceAll"), dao.calls)
        // Marker was never persisted, so a next call re-seeds -- idempotent because replaceAll is
        // a full replace, never a duplicate-insert failure.
        assertEquals(null, prefs.observeSeededCatalogVersion().value())

        val healthyPrefs = RecordingFakePrefs(initial = null)
        val seederAgain = testSeeder({ catalogJson("1.0.0") }, dao, healthyPrefs)
        seederAgain.seedIfNeeded()
        assertEquals(listOf("replaceAll", "replaceAll"), dao.calls)
        assertEquals("1.0.0|es", healthyPrefs.observeSeededCatalogVersion().value())
    }

    @Test
    fun `marker is written AFTER the dao call, never before`() = runBlocking {
        val order = mutableListOf<String>()
        val dao = object : CatalogAffirmationDao by RecordingFakeDao() {
            override suspend fun replaceAll(rows: List<CatalogAffirmationEntity>) {
                order += "dao.replaceAll"
            }
        }
        val prefs = object : CatalogPreferences {
            private val state = MutableStateFlow<String?>(null)
            override fun observeSeededCatalogVersion(): Flow<String?> = state
            override suspend fun saveSeededCatalogVersion(version: String) {
                order += "prefs.save"
                state.value = version
            }
        }
        val seeder = testSeeder({ catalogJson("1.0.0") }, dao, prefs)

        seeder.seedIfNeeded()

        assertEquals(listOf("dao.replaceAll", "prefs.save"), order)
    }

    // --- Locale-aware marker (REQ-LOC-4, S1, S2, S5, S7) ---

    @Test
    fun `marker is version pipe locale tag for each locale`() = runBlocking {
        val prefsEn = RecordingFakePrefs()
        testSeeder({ catalogJson("1.0.0") }, RecordingFakeDao(), prefsEn) { CatalogLocale.EN }.seedIfNeeded()
        assertEquals("1.0.0|en", prefsEn.observeSeededCatalogVersion().value())

        val prefsEs = RecordingFakePrefs()
        testSeeder({ catalogJson("1.0.0") }, RecordingFakeDao(), prefsEs) { CatalogLocale.ES }.seedIfNeeded()
        assertEquals("1.0.0|es", prefsEs.observeSeededCatalogVersion().value())
    }

    @Test
    fun `reads the asset of the effective locale`() = runBlocking {
        val requested = mutableListOf<CatalogLocale>()
        val seeder = testSeeder(
            { locale -> requested += locale; catalogJson("1.0.0") },
            RecordingFakeDao(),
            RecordingFakePrefs(),
        ) { CatalogLocale.EN }

        seeder.seedIfNeeded()

        assertEquals(listOf(CatalogLocale.EN), requested)
    }

    @Test
    fun `a locale change re-seeds even when the version is unchanged`() = runBlocking {
        val dao = RecordingFakeDao(existingRows = 1)
        val prefs = RecordingFakePrefs(initial = "1.0.0|es")
        val seeder = testSeeder({ catalogJson("1.0.0") }, dao, prefs) { CatalogLocale.EN }

        seeder.seedIfNeeded()

        assertEquals(listOf("replaceAll"), dao.calls)
        assertEquals("1.0.0|en", prefs.observeSeededCatalogVersion().value())
    }

    @Test
    fun `a legacy version-only marker re-seeds once`() = runBlocking {
        val dao = RecordingFakeDao(existingRows = 1)
        val prefs = RecordingFakePrefs(initial = "5.1.0-r1")
        val seeder = testSeeder({ catalogJson("5.1.0-r1") }, dao, prefs)

        seeder.seedIfNeeded()
        seeder.seedIfNeeded()

        assertEquals(listOf("replaceAll"), dao.calls)
        assertEquals("5.1.0-r1|es", prefs.observeSeededCatalogVersion().value())
    }

    @Test
    fun `matching version and locale over rows is a no-op`() = runBlocking {
        val dao = RecordingFakeDao(existingRows = 1)
        val prefs = RecordingFakePrefs(initial = "1.0.0|en")
        testSeeder({ catalogJson("1.0.0") }, dao, prefs) { CatalogLocale.EN }.seedIfNeeded()

        assertTrue(dao.calls.isEmpty())
        assertTrue(prefs.calls.isEmpty())
    }

    @Test
    fun `an explicit locale overrides the resolver`() = runBlocking {
        val prefs = RecordingFakePrefs()
        val seeder = testSeeder({ catalogJson("1.0.0") }, RecordingFakeDao(), prefs) { CatalogLocale.ES }

        seeder.seedIfNeeded(CatalogLocale.EN)

        assertEquals("1.0.0|en", prefs.observeSeededCatalogVersion().value())
    }

    @Test
    fun `no-arg seed resolves the locale inside the lock and concurrent seeds converge`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val replaced = mutableListOf<String>()
        val dao = object : CatalogAffirmationDao by RecordingFakeDao() {
            override suspend fun replaceAll(rows: List<CatalogAffirmationEntity>) {
                replaced += rows.first().text
                if (replaced.size == 1) gate.await() // hold the lock during the first (explicit) seed
            }
            override suspend fun count(): Int = 1
        }
        val prefs = RecordingFakePrefs()
        var current = CatalogLocale.EN
        val seeder = testSeeder(
            { locale -> catalogJson(if (locale == CatalogLocale.EN) "1.0.0-en" else "1.0.0-es") },
            dao,
            prefs,
        ) { current }

        val explicit = launch { seeder.seedIfNeeded(CatalogLocale.EN) }
        runCurrent()
        val reconcile = launch { seeder.seedIfNeeded() } // queued behind the lock, resolves LATER
        runCurrent()
        current = CatalogLocale.ES // effective locale changes while the first seed holds the lock
        gate.complete(Unit)
        advanceUntilIdle()

        explicit.join()
        reconcile.join()
        assertEquals(2, replaced.size)
        assertEquals("1.0.0-es|es", prefs.observeSeededCatalogVersion().value())
    }

    @Test
    fun `re-seed uses a single replaceAll and writes the marker after it`() = runBlocking {
        val order = mutableListOf<String>()
        val dao = object : CatalogAffirmationDao by RecordingFakeDao(existingRows = 1) {
            override suspend fun replaceAll(rows: List<CatalogAffirmationEntity>) {
                order += "replaceAll"
            }
            override suspend fun deleteAll() = throw AssertionError("separate delete is not allowed")
            override suspend fun insertAll(rows: List<CatalogAffirmationEntity>) =
                throw AssertionError("separate insert is not allowed")
        }
        val prefs = object : CatalogPreferences {
            private val state = MutableStateFlow<String?>("1.0.0|es")
            override fun observeSeededCatalogVersion(): Flow<String?> = state
            override suspend fun saveSeededCatalogVersion(version: String) {
                order += "marker:$version"
                state.value = version
            }
        }

        testSeeder({ catalogJson("1.0.0") }, dao, prefs) { CatalogLocale.EN }.seedIfNeeded()

        assertEquals(listOf("replaceAll", "marker:1.0.0|en"), order)
    }
}

private fun <T> Flow<T>.value(): T = (this as MutableStateFlow<T>).value
