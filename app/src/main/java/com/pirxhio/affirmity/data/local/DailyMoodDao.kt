package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyMoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyMoodEntity)

    @Query("SELECT * FROM daily_mood WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>>

    @Query("SELECT * FROM daily_mood WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity>
}
