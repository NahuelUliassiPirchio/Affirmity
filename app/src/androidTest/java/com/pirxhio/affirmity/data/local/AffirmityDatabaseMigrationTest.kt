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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
