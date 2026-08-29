package com.pirxhio.affirmity.data.catalog

import com.pirxhio.affirmity.data.local.CatalogAffirmationDao
import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
import com.pirxhio.affirmity.data.local.CatalogPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
            """{"id":"cat_$COLLECTION_ID.00$i","text":"Texto $i","groupId":"self_worth",""" +
                """"themeId":"self_worth.feeling_enough","collectionId":"$COLLECTION_ID","sortOrder":${i - 1}}""",
        )
    }
    append("]}")
}

private class RecordingFakeDao : CatalogAffirmationDao {
    val calls = mutableListOf<String>()
    var lastReplaced: List<CatalogAffirmationEntity> = emptyList()

    override fun observeAll(): Flow<List<CatalogAffirmationEntity>> = throw NotImplementedError()
    override fun observeByGroupIds(groupIds: Set<String>): Flow<List<CatalogAffirmationEntity>> = throw NotImplementedError()
    override suspend fun getByIds(ids: List<String>): List<CatalogAffirmationEntity> = emptyList()
    override suspend fun count(): Int = lastReplaced.size

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

class CatalogSeederTest {

    @Test
    fun `seeds when the marker is absent`() = runBlocking {
        val dao = RecordingFakeDao()
        val prefs = RecordingFakePrefs(initial = null)
        val seeder = CatalogSeeder(
            assetReader = { catalogJson("1.0.0") },
            dao = dao,
            prefs = prefs,
            knownCollectionIds = { KNOWN_COLLECTION_IDS },
        )

        seeder.seedIfNeeded()

        assertEquals(listOf("replaceAll"), dao.calls)
        assertEquals(1, dao.lastReplaced.size)
        assertEquals("1.0.0", prefs.observeSeededCatalogVersion().value())
    }

    @Test
    fun `seeds when the marker is stale`() = runBlocking {
        val dao = RecordingFakeDao()
        val prefs = RecordingFakePrefs(initial = "0.9.0")
        val seeder = CatalogSeeder({ catalogJson("1.0.0") }, dao, prefs) { KNOWN_COLLECTION_IDS }

        seeder.seedIfNeeded()

        assertEquals(listOf("replaceAll"), dao.calls)
        assertEquals("1.0.0", prefs.observeSeededCatalogVersion().value())
    }

    @Test
    fun `no-ops when the marker already matches the bundled version`() = runBlocking {
        val dao = RecordingFakeDao()
        val prefs = RecordingFakePrefs(initial = "1.0.0")
        val seeder = CatalogSeeder({ catalogJson("1.0.0") }, dao, prefs) { KNOWN_COLLECTION_IDS }

        seeder.seedIfNeeded()

        assertTrue(dao.calls.isEmpty())
    }

    @Test
    fun `a throwing saveSeededCatalogVersion still leaves rows committed and re-seeds cleanly next call`() = runBlocking {
        val dao = RecordingFakeDao()
        val prefs = RecordingFakePrefs(initial = null, throwOnSave = true)
        val seeder = CatalogSeeder({ catalogJson("1.0.0") }, dao, prefs) { KNOWN_COLLECTION_IDS }

        assertThrows(IllegalStateException::class.java) { runBlocking { seeder.seedIfNeeded() } }
        assertEquals(listOf("replaceAll"), dao.calls)
        // Marker was never persisted, so a next call re-seeds -- idempotent because replaceAll is
        // a full replace, never a duplicate-insert failure.
        assertEquals(null, prefs.observeSeededCatalogVersion().value())

        val healthyPrefs = RecordingFakePrefs(initial = null)
        val seederAgain = CatalogSeeder({ catalogJson("1.0.0") }, dao, healthyPrefs) { KNOWN_COLLECTION_IDS }
        seederAgain.seedIfNeeded()
        assertEquals(listOf("replaceAll", "replaceAll"), dao.calls)
        assertEquals("1.0.0", healthyPrefs.observeSeededCatalogVersion().value())
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
        val seeder = CatalogSeeder({ catalogJson("1.0.0") }, dao, prefs) { KNOWN_COLLECTION_IDS }

        seeder.seedIfNeeded()

        assertEquals(listOf("dao.replaceAll", "prefs.save"), order)
    }
}

private fun <T> Flow<T>.value(): T = (this as MutableStateFlow<T>).value
