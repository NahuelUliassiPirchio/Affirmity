package com.pirxhio.affirmity.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

/** JVM guard for the SQL; the real upgrade path is covered by ActivityJournalMigrationTest on device. */
class ActivityOwnerImportedMigrationTest {
    @Test fun `18 to 19 adds a nullable imported timestamp and nothing else`() {
        val db = Mockito.mock(SupportSQLiteDatabase::class.java)
        MIGRATION_18_19.migrate(db)
        val statements = mutableListOf<String>()
        Mockito.mockingDetails(db).invocations
            .filter { it.method.name == "execSQL" }
            .forEach { statements += it.arguments[0] as String }
        assertEquals(listOf("ALTER TABLE `activity_owner` ADD COLUMN `importedAtMillis` INTEGER"), statements)
        assertEquals(18, MIGRATION_18_19.startVersion)
        assertEquals(19, MIGRATION_18_19.endVersion)
    }
}
