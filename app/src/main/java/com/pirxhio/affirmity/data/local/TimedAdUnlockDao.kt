package com.pirxhio.affirmity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimedAdUnlockDao {

    /** REPLACE, not IGNORE -- the whole point (design D16): re-earning a window after expiry is
     * an overwrite of [TimedAdUnlockEntity.grantedAtMillis]/[TimedAdUnlockEntity.expiresAtMillis],
     * unlike [AdUnlockDao.insertIfAbsent]'s create-if-absent semantics. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TimedAdUnlockEntity)

    @Query("SELECT * FROM timed_ad_unlock")
    fun observeAll(): Flow<List<TimedAdUnlockEntity>>

    @Query("SELECT * FROM timed_ad_unlock")
    suspend fun getAll(): List<TimedAdUnlockEntity>
}
