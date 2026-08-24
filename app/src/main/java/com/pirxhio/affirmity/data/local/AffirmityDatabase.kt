package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive: creates `daily_completion` empty. No backfill from the old streak-only history. */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `daily_completion` (
                `epochDay` INTEGER NOT NULL,
                `meditationDone` INTEGER NOT NULL DEFAULT 0,
                `affirmationDone` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`epochDay`)
            )
            """.trimIndent(),
        )
    }
}

/** Additive: creates `daily_mood` empty. No backfill — mood tracking starts from this version. */
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `daily_mood` (
                `epochDay` INTEGER NOT NULL,
                `moodValue` INTEGER NOT NULL,
                `note` TEXT,
                PRIMARY KEY(`epochDay`)
            )
            """.trimIndent(),
        )
    }
}

/** Additive: creates `streak_healer_use` empty. No backfill — the healer log starts from this
 * version (design.md's "Migration / Rollout": `HEALER_EPOCH_START_DAY` blocks retroactive healing). */
val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `streak_healer_use` (
                `healedEpochDay` INTEGER NOT NULL,
                `activatedAtMillis` INTEGER NOT NULL,
                PRIMARY KEY(`healedEpochDay`)
            )
            """.trimIndent(),
        )
    }
}

/** First ALTER-on-existing-table migration in this codebase. Structural only: the NOT NULL
 * DEFAULT backfills every pre-existing row to `personalizadas` in one statement, so no user
 * affirmation can become invisible under the new group filter. No content is inserted here or
 * anywhere else in this change — the thematic groups ship as empty categories. */
val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `affirmations` ADD COLUMN `groupId` TEXT NOT NULL DEFAULT 'personalizadas'"
        )
    }
}

/** Additive: creates `ad_unlock` empty. No backfill — durable ad-unlock persistence starts from
 * this version (design §4a); PER_USE unlocks are never persisted (design §0/§4b), so there is
 * nothing to migrate from a prior in-memory-only state. */
val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ad_unlock` (
                `contentKey` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `contentId` TEXT NOT NULL,
                `grantedAtMillis` INTEGER NOT NULL,
                `expiresAtMillis` INTEGER,
                PRIMARY KEY(`contentKey`)
            )
            """.trimIndent(),
        )
    }
}

/** Additive, mirrors MIGRATION_4_5 exactly. The NOT NULL DEFAULT '{}' backfills every
 * pre-existing row with an empty override map in one statement, so no affirmation can become
 * unreadable by the new TypeConverter (which would otherwise see NULL). No content is inserted
 * or altered. */
val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `affirmations` ADD COLUMN `overrides` TEXT NOT NULL DEFAULT '{}'")
    }
}

@Database(
    entities = [
        AffirmationEntity::class,
        DailyCompletionEntity::class,
        DailyMoodEntity::class,
        StreakHealerUseEntity::class,
        AdUnlockEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@androidx.room.TypeConverters(OverridesConverters::class)
abstract class AffirmityDatabase : RoomDatabase() {
    abstract fun affirmationDao(): AffirmationDao
    abstract fun dailyCompletionDao(): DailyCompletionDao
    abstract fun dailyMoodDao(): DailyMoodDao
    abstract fun streakHealerUseDao(): StreakHealerUseDao
    abstract fun adUnlockDao(): AdUnlockDao

    companion object {
        @Volatile
        private var instance: AffirmityDatabase? = null

        fun getInstance(context: Context): AffirmityDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AffirmityDatabase::class.java,
                    "affirmity.db",
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                ).build().also { instance = it }
            }
    }
}
