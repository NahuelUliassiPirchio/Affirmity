package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyCompletionDao {

    @Transaction
    suspend fun markMeditation(epochDay: Long) {
        insertIfAbsent(DailyCompletionEntity(epochDay = epochDay))
        setMeditationDone(epochDay)
    }

    @Transaction
    suspend fun markAffirmation(epochDay: Long) {
        insertIfAbsent(DailyCompletionEntity(epochDay = epochDay))
        setAffirmationDone(epochDay)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: DailyCompletionEntity)

    @Query("UPDATE daily_completion SET meditationDone = 1 WHERE epochDay = :epochDay")
    suspend fun setMeditationDone(epochDay: Long)

    @Query("UPDATE daily_completion SET affirmationDone = 1 WHERE epochDay = :epochDay")
    suspend fun setAffirmationDone(epochDay: Long)

    @Query("SELECT * FROM daily_completion WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>>

    @Query("SELECT * FROM daily_completion WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM daily_completion)")
    suspend fun hasAny(): Boolean
}
