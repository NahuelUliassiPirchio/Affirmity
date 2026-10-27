package com.pirxhio.affirmity.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActivityJournalMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AffirmityDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun upgradeRetainsMoodAndCompletionWithoutInventingJournalFacts() {
        helper.createDatabase("activity-upgrade-test", 16).apply {
            execSQL("INSERT INTO daily_mood (epochDay, moodValue, note) VALUES (100, 3, 'Retained')")
            execSQL("INSERT INTO daily_completion (epochDay, meditationDone, affirmationDone) VALUES (100, 1, 0)")
            close()
        }
        helper.runMigrationsAndValidate("activity-upgrade-test", 17, true, MIGRATION_16_17).use { db ->
            db.query("SELECT moodValue, note FROM daily_mood WHERE epochDay = 100").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
                assertEquals("Retained", cursor.getString(1))
            }
            db.query("SELECT meditationDone, affirmationDone FROM daily_completion WHERE epochDay = 100").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals(0, cursor.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM activity_event").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test fun upgrade17To18RetainsJournalAndAddsEmptyScopedFacts() {
        helper.createDatabase("activity-upgrade-18-test", 17).apply {
            execSQL("INSERT INTO activity_owner (owner, sourceId, claimedUid, active) VALUES ('local:first', 'device', 'A', 0)")
            execSQL("INSERT INTO activity_event (owner, eventId, epochDay, kind, occurredAtMillis, startMillis, endMillis, zoneId, meditationId, nameSnapshot, activeSeconds, sourceId, uploadState) " +
                "VALUES ('account:A', 'event', 100, 'guided', 1000, 0, 1000, 'UTC', 'breathing', 'Breathing', 120, 'device', 'PENDING')")
            close()
        }
        helper.runMigrationsAndValidate("activity-upgrade-18-test", 18, true, MIGRATION_17_18).use { db ->
            db.query("SELECT activeSeconds, uploadState FROM activity_event WHERE eventId = 'event'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(120L, cursor.getLong(0))
                assertEquals("PENDING", cursor.getString(1))
            }
            db.query("SELECT claimedUid, legacyAdoptionComplete FROM activity_owner WHERE owner = 'local:first'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("A", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM activity_daily_fact").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test fun fresh16To18ChainRetainsLegacyMood() {
        helper.createDatabase("activity-upgrade-chain-test", 16).apply {
            execSQL("INSERT INTO daily_mood (epochDay, moodValue, note) VALUES (100, 3, 'Retained')")
            close()
        }
        helper.runMigrationsAndValidate("activity-upgrade-chain-test", 18, true, MIGRATION_16_17, MIGRATION_17_18).use { db ->
            db.query("SELECT moodValue, note FROM daily_mood WHERE epochDay = 100").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
                assertEquals("Retained", cursor.getString(1))
            }
        }
    }
}
