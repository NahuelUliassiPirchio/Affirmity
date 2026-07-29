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

@Database(
    entities = [AffirmationEntity::class, DailyCompletionEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AffirmityDatabase : RoomDatabase() {
    abstract fun affirmationDao(): AffirmationDao
    abstract fun dailyCompletionDao(): DailyCompletionDao

    companion object {
        @Volatile
        private var instance: AffirmityDatabase? = null

        fun getInstance(context: Context): AffirmityDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AffirmityDatabase::class.java,
                    "affirmity.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
