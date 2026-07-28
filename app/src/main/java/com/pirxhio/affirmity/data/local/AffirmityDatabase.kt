package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AffirmationEntity::class], version = 1, exportSchema = false)
abstract class AffirmityDatabase : RoomDatabase() {
    abstract fun affirmationDao(): AffirmationDao

    companion object {
        @Volatile
        private var instance: AffirmityDatabase? = null

        fun getInstance(context: Context): AffirmityDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AffirmityDatabase::class.java,
                    "affirmity.db",
                ).build().also { instance = it }
            }
    }
}
