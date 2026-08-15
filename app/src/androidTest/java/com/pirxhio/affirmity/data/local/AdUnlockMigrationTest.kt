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
 * [ENVIRONMENT-BLOCKED] Covers task C.14: [MIGRATION_5_6] correctness, following
 * [AffirmityDatabaseMigrationTest]'s exact `MigrationTestHelper` pattern as a SEPARATE new file
 * (that existing file is deliberately left untouched). Verifies the empty `ad_unlock` table is
 * created with the exact [AdUnlockEntity] schema, and pre-existing v5 tables/rows are untouched.
 * Execution requires a connected device/emulator (`connectedDebugAndroidTest`), unavailable in
 * this sandbox.
 */
class AdUnlockMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AffirmityDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate5To6_createsEmptyAdUnlockTable_andLeavesExistingAffirmationsUntouched() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                "INSERT INTO affirmations (id, title, subtitle, backgroundType, backgroundValue, groupId) " +
                    "VALUES ('id-1', 'Title', 'Subtitle', 'color', '#000000', 'personalizadas')",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        val adUnlockCursor = migrated.query("SELECT * FROM ad_unlock")
        assertFalse("ad_unlock must be created empty, no backfill", adUnlockCursor.moveToFirst())
        adUnlockCursor.close()

        val affirmationsCursor = migrated.query("SELECT groupId FROM affirmations WHERE id = 'id-1'")
        assertTrue("pre-existing v5 rows must survive the migration untouched", affirmationsCursor.moveToFirst())
        assertEquals("personalizadas", affirmationsCursor.getString(0))
        affirmationsCursor.close()
    }

    @Test
    fun migrate5To6_adUnlockTableAcceptsARowMatchingTheExactAdUnlockEntitySchema() {
        helper.createDatabase(TEST_DB, 5).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        migrated.execSQL(
            "INSERT INTO ad_unlock (contentKey, contentType, contentId, grantedAtMillis, expiresAtMillis) " +
                "VALUES ('affirmationGroup_fuerza_de_voluntad', 'affirmationGroup', 'fuerza_de_voluntad', 1000, NULL)",
        )

        val cursor = migrated.query("SELECT contentKey, contentType, contentId, grantedAtMillis, expiresAtMillis FROM ad_unlock")
        assertTrue(cursor.moveToFirst())
        assertEquals("affirmationGroup_fuerza_de_voluntad", cursor.getString(0))
        assertEquals("affirmationGroup", cursor.getString(1))
        assertEquals("fuerza_de_voluntad", cursor.getString(2))
        assertEquals(1000L, cursor.getLong(3))
        assertTrue(cursor.isNull(4))
        cursor.close()
    }

    private companion object {
        const val TEST_DB = "ad-unlock-migration-test"
    }
}
