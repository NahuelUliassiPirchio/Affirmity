package com.pirxhio.affirmity.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Covers task 1.9: existing v1 data survives Migration(1,2), `daily_completion` is created empty
 * (no backfill from prior streak history), and the app does not crash on upgrade.
 */
class AffirmityDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AffirmityDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate4To5_addsGroupIdAndBackfillsExistingRowsToPersonalizadas() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                "INSERT INTO affirmations (id, title, subtitle, backgroundType, backgroundValue) " +
                    "VALUES ('id-1', 'Title', 'Subtitle', 'color', '#000000')",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        val cursor = migrated.query("SELECT groupId FROM affirmations WHERE id = 'id-1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("personalizadas", cursor.getString(0))
        assertFalse(cursor.isNull(0))
        cursor.close()
    }

    @Test
    fun migrate6To7_addsOverridesColumnAndBackfillsExistingRowsToEmptyMap() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO affirmations (id, title, subtitle, backgroundType, backgroundValue, groupId) " +
                    "VALUES ('id-1', 'Title', 'Subtitle', 'color', '#000000', 'personalizadas')",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)

        val cursor = migrated.query(
            "SELECT title, subtitle, backgroundType, backgroundValue, groupId, overrides " +
                "FROM affirmations WHERE id = 'id-1'",
        )
        assertTrue(cursor.moveToFirst())
        assertEquals("Title", cursor.getString(0))
        assertEquals("Subtitle", cursor.getString(1))
        assertEquals("color", cursor.getString(2))
        assertEquals("#000000", cursor.getString(3))
        assertEquals("personalizadas", cursor.getString(4))
        assertEquals("{}", cursor.getString(5))
        assertFalse(cursor.isNull(5))
        cursor.close()
    }

    @Test
    fun migrate7To8_createsEmptyFavoriteAffirmationsTableAndPreservesAffirmations() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL(
                "INSERT INTO affirmations " +
                    "(id, title, subtitle, backgroundType, backgroundValue, groupId, overrides) " +
                    "VALUES ('id-1', 'Title', 'Subtitle', 'color', '#000000', " +
                    "'personalizadas', '{\"title:0:name\":\"Alex\"}')",
            )
            execSQL(
                "INSERT INTO daily_completion " +
                    "(epochDay, meditationDone, affirmationDone) VALUES (123, 1, 0)",
            )
            execSQL(
                "INSERT INTO ad_unlock " +
                    "(contentKey, contentType, contentId, grantedAtMillis, expiresAtMillis) " +
                    "VALUES ('key', 'affirmationGroup', 'group', 1000, NULL)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        migrated.query(
            "SELECT title, subtitle, backgroundType, backgroundValue, groupId, overrides " +
                "FROM affirmations WHERE id = 'id-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Title", cursor.getString(0))
            assertEquals("Subtitle", cursor.getString(1))
            assertEquals("color", cursor.getString(2))
            assertEquals("#000000", cursor.getString(3))
            assertEquals("personalizadas", cursor.getString(4))
            assertEquals("{\"title:0:name\":\"Alex\"}", cursor.getString(5))
        }
        migrated.query("SELECT meditationDone, affirmationDone FROM daily_completion WHERE epochDay = 123")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals(0, cursor.getInt(1))
            }
        migrated.query("SELECT contentKey FROM ad_unlock WHERE contentKey = 'key'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("key", cursor.getString(0))
        }
        migrated.query("SELECT * FROM favorite_affirmations").use { cursor ->
            assertFalse(cursor.moveToFirst())
        }
    }

    @Test
    fun migrate1To2_preservesAffirmationsAndCreatesEmptyDailyCompletionTable() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO affirmations (id, title, subtitle, backgroundType, backgroundValue) " +
                    "VALUES ('id-1', 'Title', 'Subtitle', 'color', '#000000')",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        val affirmationsCursor = migrated.query("SELECT * FROM affirmations")
        assertTrue(affirmationsCursor.moveToFirst())
        affirmationsCursor.close()

        val dailyCompletionCursor = migrated.query("SELECT * FROM daily_completion")
        assertFalse(dailyCompletionCursor.moveToFirst())
        dailyCompletionCursor.close()
    }

    @Test
    fun migrate8To9_createsEmptyTimedAdUnlockTableAndLeavesAdUnlockUntouched() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                "INSERT INTO ad_unlock " +
                    "(contentKey, contentType, contentId, grantedAtMillis, expiresAtMillis) " +
                    "VALUES ('key', 'affirmationGroup', 'group', 1000, NULL)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9)

        // `timed_ad_unlock` (design D16) is created EMPTY -- no backfill from `ad_unlock`, they are
        // two SEPARATE stores from day one.
        migrated.query("SELECT * FROM timed_ad_unlock").use { cursor ->
            assertFalse(cursor.moveToFirst())
        }
        // Pre-existing `ad_unlock` row is byte-for-byte untouched by this migration.
        migrated.query(
            "SELECT contentType, contentId, grantedAtMillis, expiresAtMillis FROM ad_unlock WHERE contentKey = 'key'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("affirmationGroup", cursor.getString(0))
            assertEquals("group", cursor.getString(1))
            assertEquals(1000L, cursor.getLong(2))
            assertTrue(cursor.isNull(3))
        }
    }

    /** Extends the task-1.8 suite (task 2.11, Phase 2 of design.md's catalog foundation):
     * `migrate8To9` now also creates BOTH catalog tables empty, both catalog indices present, and
     * every pre-existing column -- including `overrides` -- untouched. */
    @Test
    fun migrate8To9_createsAllThreeNewTablesEmptyWithCatalogIndicesAndLeavesExistingColumnsUntouched() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                "INSERT INTO affirmations " +
                    "(id, title, subtitle, backgroundType, backgroundValue, groupId, overrides) " +
                    "VALUES ('id-1', 'Title', 'Subtitle', 'color', '#000000', 'personalizadas', '{\"k\":\"v\"}')",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9)

        migrated.query("SELECT * FROM timed_ad_unlock").use { cursor -> assertFalse(cursor.moveToFirst()) }
        migrated.query("SELECT * FROM catalog_affirmations").use { cursor -> assertFalse(cursor.moveToFirst()) }
        migrated.query("SELECT * FROM catalog_affirmation_overrides").use { cursor -> assertFalse(cursor.moveToFirst()) }

        migrated.query("PRAGMA index_list(`catalog_affirmations`)").use { cursor ->
            val indexNames = generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }.toList()
            assertTrue(indexNames.contains("index_catalog_affirmations_groupId"))
            assertTrue(indexNames.contains("index_catalog_affirmations_collectionId"))
        }

        migrated.query(
            "SELECT title, subtitle, backgroundType, backgroundValue, groupId, overrides " +
                "FROM affirmations WHERE id = 'id-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Title", cursor.getString(0))
            assertEquals("Subtitle", cursor.getString(1))
            assertEquals("color", cursor.getString(2))
            assertEquals("#000000", cursor.getString(3))
            assertEquals("personalizadas", cursor.getString(4))
            assertEquals("{\"k\":\"v\"}", cursor.getString(5))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
