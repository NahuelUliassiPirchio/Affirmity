package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AdUnlockDao {

    /** IGNORE, not REPLACE: create-if-absent is the trial's non-repeatability invariant
     * (design §4a) — a second insert for an existing [AdUnlockEntity.contentKey] is a silent
     * no-op, never an overwrite. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: AdUnlockEntity)

    @Query("SELECT * FROM ad_unlock")
    fun observeAll(): Flow<List<AdUnlockEntity>>

    @Query("SELECT * FROM ad_unlock")
    suspend fun getAll(): List<AdUnlockEntity>
}
