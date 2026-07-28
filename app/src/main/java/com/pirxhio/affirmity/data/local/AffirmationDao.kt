package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AffirmationDao {
    @Query("SELECT * FROM affirmations ORDER BY rowid ASC")
    fun observeAll(): Flow<List<AffirmationEntity>>

    @Insert
    suspend fun insert(entity: AffirmationEntity)

    @Query("DELETE FROM affirmations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM affirmations ORDER BY RANDOM() LIMIT 1")
    suspend fun randomAffirmation(): AffirmationEntity?

    @Query("DELETE FROM affirmations")
    suspend fun deleteAll()
}
