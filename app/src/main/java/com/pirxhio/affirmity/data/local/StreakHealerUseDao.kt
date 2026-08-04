package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StreakHealerUseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StreakHealerUseEntity)

    @Query("SELECT * FROM streak_healer_use WHERE healedEpochDay BETWEEN :from AND :to ORDER BY healedEpochDay")
    fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>>

    @Query("SELECT * FROM streak_healer_use WHERE healedEpochDay BETWEEN :from AND :to ORDER BY healedEpochDay")
    suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity>
}
