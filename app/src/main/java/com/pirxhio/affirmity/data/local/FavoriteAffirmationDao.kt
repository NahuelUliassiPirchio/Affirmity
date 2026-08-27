package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteAffirmationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteAffirmationEntity)

    @Query("DELETE FROM favorite_affirmations WHERE affirmationId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM favorite_affirmations")
    suspend fun deleteAll()

    @Query("SELECT affirmationId FROM favorite_affirmations ORDER BY favoritedAtMillis DESC")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_affirmations WHERE affirmationId = :id)")
    suspend fun isFavorite(id: String): Boolean
}
